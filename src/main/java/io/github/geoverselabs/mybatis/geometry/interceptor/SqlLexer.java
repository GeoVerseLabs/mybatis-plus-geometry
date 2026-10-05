package io.github.geoverselabs.mybatis.geometry.interceptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Minimal, linear-time SQL tokenizer used to find the structure of SELECT statements.
 *
 * <p>Whitespace and comments ({@code -- ...}, {@code # ...} for MySQL and block comments, including
 * optimizer hints such as {@code /*+ ... *&#47;}) produce no tokens, but token offsets always refer to
 * the original text, so callers can splice the statement without losing them. Quoted text
 * ({@code '...'}, {@code "..."}, backticks, prefixed literals such as {@code X'..'} or PostgreSQL
 * {@code E'...'}, and dollar quotes) is a single token, so separators and keywords inside it are never
 * seen. Brackets ({@code ( [ {}) must pair up and every token records its nesting depth.</p>
 *
 * <p>The lexer never throws: malformed SQL (unterminated quotes or comments, unbalanced brackets)
 * yields {@code null}, which callers treat as "leave the statement alone".</p>
 */
final class SqlLexer {

    /** Token kinds. */
    enum Kind {
        /** Unquoted identifier or keyword. */
        WORD,
        /** Quoted identifier: backticks, or double quotes where they quote identifiers. */
        QUOTED_IDENT,
        /** String literal (single quotes, prefixed or dollar quoted, MySQL double quotes). */
        STRING,
        /** Numeric literal. */
        NUMBER,
        /** {@code (}, {@code [} or <code>{</code>. */
        OPEN,
        /** {@code )}, {@code ]} or <code>}</code>. */
        CLOSE,
        /** {@code ,}. */
        COMMA,
        /** {@code .}. */
        DOT,
        /** {@code *}. */
        STAR,
        /** {@code ;}. */
        SEMICOLON,
        /** Any other character (operators, parameter markers, ...); one token per character. */
        OTHER
    }

    /**
     * A token: kind, source range {@code [start, end)} and bracket depth.
     */
    static final class Token {
        final Kind kind;
        final int start;
        final int end;
        /** Number of enclosing brackets; a bracket token has the depth of its surroundings. */
        final int depth;

        Token(Kind kind, int start, int end, int depth) {
            this.kind = kind;
            this.start = start;
            this.end = end;
            this.depth = depth;
        }

        @Override
        public String toString() {
            return kind + "[" + start + "," + end + ")@" + depth;
        }
    }

    /** Longest keyword looked up in a keyword set; longer words are never keywords. */
    private static final int MAX_KEYWORD_LENGTH = 32;

    private final String sql;
    private final SqlDialect dialect;
    private final int length;
    private final List<Token> tokens = new ArrayList<>();
    private char[] openStack = new char[8];
    private int openCount;

    private SqlLexer(String sql, SqlDialect dialect) {
        this.sql = sql;
        this.dialect = dialect;
        this.length = sql.length();
    }

    /**
     * Tokenize {@code sql}.
     *
     * @param sql     the SQL text
     * @param dialect lexical rules
     * @return the tokens (unmodifiable), or {@code null} when {@code sql} is null or malformed
     *         (unterminated quote or comment, unbalanced brackets)
     */
    static List<Token> tokenize(String sql, SqlDialect dialect) {
        if (sql == null) {
            return null;
        }
        return new SqlLexer(sql, dialect).run();
    }

    /**
     * Whether {@code token} is an unquoted word equal to {@code keyword} (ASCII case-insensitive).
     */
    static boolean isKeyword(String sql, Token token, String keyword) {
        return token.kind == Kind.WORD
            && token.end - token.start == keyword.length()
            && sql.regionMatches(true, token.start, keyword, 0, keyword.length());
    }

    /**
     * Whether {@code token} is an unquoted word contained in {@code keywords} (upper case entries).
     */
    static boolean isAnyKeyword(String sql, Token token, Set<String> keywords) {
        if (token.kind != Kind.WORD || token.end - token.start > MAX_KEYWORD_LENGTH) {
            return false;
        }
        return keywords.contains(sql.substring(token.start, token.end).toUpperCase(Locale.ROOT));
    }

    private List<Token> run() {
        int i = 0;
        while (i < length) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '-' && startsDashDashComment(i)) {
                i = endOfLine(i + 2);
            } else if (c == '#' && dialect.hashComments()) {
                i = endOfLine(i + 1);
            } else if (c == '/' && i + 1 < length && sql.charAt(i + 1) == '*') {
                i = skipBlockComment(i);
            } else if (c == '\'') {
                i = addQuoted(Kind.STRING, i, i, '\'', dialect.backslashEscapes());
            } else if (c == '"') {
                Kind kind = dialect.doubleQuotedIdentifiers() ? Kind.QUOTED_IDENT : Kind.STRING;
                i = addQuoted(kind, i, i, '"', dialect.backslashEscapes());
            } else if (c == '`') {
                i = addQuoted(Kind.QUOTED_IDENT, i, i, '`', false);
            } else if (c == '$' && dialect.dollarQuotes() && !precededByIdentifierPart(i)
                && dollarTagEnd(i) > 0) {
                i = addDollarQuoted(i);
            } else if (isIdentifierStart(c)) {
                i = addWord(i);
            } else if (c >= '0' && c <= '9') {
                i = addNumber(i);
            } else if (c == '(' || c == '[' || c == '{') {
                add(Kind.OPEN, i, i + 1);
                push(c);
                i++;
            } else if (c == ')' || c == ']' || c == '}') {
                i = close(c, i);
            } else {
                Kind kind = switch (c) {
                    case ',' -> Kind.COMMA;
                    case '.' -> Kind.DOT;
                    case '*' -> Kind.STAR;
                    case ';' -> Kind.SEMICOLON;
                    default -> Kind.OTHER;
                };
                add(kind, i, i + 1);
                i++;
            }
            if (i < 0) {
                return null;
            }
        }
        if (openCount != 0) {
            return null;
        }
        return Collections.unmodifiableList(tokens);
    }

    private void add(Kind kind, int start, int end) {
        tokens.add(new Token(kind, start, end, openCount));
    }

    private void push(char open) {
        if (openCount == openStack.length) {
            char[] grown = new char[openStack.length * 2];
            System.arraycopy(openStack, 0, grown, 0, openCount);
            openStack = grown;
        }
        openStack[openCount++] = open;
    }

    /**
     * Close the innermost bracket.
     *
     * @return the index after the bracket, or -1 when it does not match the innermost open bracket
     */
    private int close(char c, int position) {
        if (openCount == 0) {
            return -1;
        }
        char open = openStack[openCount - 1];
        if ((c == ')' && open != '(') || (c == ']' && open != '[') || (c == '}' && open != '{')) {
            return -1;
        }
        openCount--;
        add(Kind.CLOSE, position, position + 1);
        return position + 1;
    }

    private boolean startsDashDashComment(int i) {
        if (i + 1 >= length || sql.charAt(i + 1) != '-') {
            return false;
        }
        if (!dialect.dashDashNeedsSpace() || i + 2 >= length) {
            return true;
        }
        // MySQL: "--" starts a comment only when followed by whitespace or a control character
        char next = sql.charAt(i + 2);
        return Character.isWhitespace(next) || Character.isISOControl(next);
    }

    private int endOfLine(int from) {
        for (int i = from; i < length; i++) {
            char c = sql.charAt(i);
            if (c == '\n' || c == '\r') {
                return i;
            }
        }
        return length;
    }

    /**
     * @return the index after the comment, or -1 when it is not terminated
     */
    private int skipBlockComment(int start) {
        int level = 1;
        int i = start + 2;
        while (i < length) {
            char c = sql.charAt(i);
            if (c == '*' && i + 1 < length && sql.charAt(i + 1) == '/') {
                i += 2;
                if (--level == 0) {
                    return i;
                }
            } else if (c == '/' && i + 1 < length && sql.charAt(i + 1) == '*'
                && dialect.nestedBlockComments()) {
                level++;
                i += 2;
            } else {
                i++;
            }
        }
        return -1;
    }

    /**
     * Add a quoted token that starts at {@code tokenStart} and whose opening quote is at
     * {@code quotePos}. A doubled quote character is an escaped quote.
     *
     * @return the index after the closing quote, or -1 when the text is not terminated
     */
    private int addQuoted(Kind kind, int tokenStart, int quotePos, char quote, boolean backslash) {
        int i = quotePos + 1;
        while (i < length) {
            char c = sql.charAt(i);
            if (backslash && c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote) {
                if (i + 1 < length && sql.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                add(kind, tokenStart, i + 1);
                return i + 1;
            }
            i++;
        }
        return -1;
    }

    /**
     * Add an unquoted word, or a prefixed string literal ({@code X'..'}, {@code B'..'},
     * {@code N'..'}, PostgreSQL {@code E'..'}, MySQL {@code _charset'..'}) when a quote follows
     * the prefix directly.
     */
    private int addWord(int start) {
        int end = start + 1;
        while (end < length && isIdentifierPart(sql.charAt(end))) {
            end++;
        }
        if (end < length && sql.charAt(end) == '\'' && isStringPrefix(start, end)) {
            boolean escape = sql.charAt(start) == 'e' || sql.charAt(start) == 'E';
            return addQuoted(Kind.STRING, start, end, '\'', dialect.backslashEscapes() || escape);
        }
        add(Kind.WORD, start, end);
        return end;
    }

    private boolean isStringPrefix(int start, int end) {
        if (end - start == 1) {
            char p = Character.toUpperCase(sql.charAt(start));
            return p == 'X' || p == 'B' || p == 'N' || (p == 'E' && dialect == SqlDialect.POSTGRESQL);
        }
        // MySQL character set introducer, e.g. _utf8mb4'text'
        return sql.charAt(start) == '_' && dialect == SqlDialect.MYSQL;
    }

    /**
     * Add a numeric literal. With MySQL rules a digit run followed by identifier characters that do
     * not form a number ({@code 2d_point}) is an identifier.
     */
    private int addNumber(int start) {
        int i = start;
        while (i < length && isDigit(sql.charAt(i))) {
            i++;
        }
        boolean fraction = false;
        if (i < length && sql.charAt(i) == '.') {
            fraction = true;
            i++;
            while (i < length && isDigit(sql.charAt(i))) {
                i++;
            }
        }
        if (i < length && (sql.charAt(i) == 'e' || sql.charAt(i) == 'E')) {
            int j = i + 1;
            if (j < length && (sql.charAt(j) == '+' || sql.charAt(j) == '-')) {
                j++;
            }
            if (j < length && isDigit(sql.charAt(j))) {
                i = j;
                while (i < length && isDigit(sql.charAt(i))) {
                    i++;
                }
            }
        }
        int end = i;
        while (end < length && isIdentifierPart(sql.charAt(end))) {
            end++;
        }
        Kind kind = Kind.NUMBER;
        if (end > i && !fraction && dialect.digitLeadingIdentifiers() && !isRadixLiteral(start, end)) {
            kind = Kind.WORD;
        }
        add(kind, start, end);
        return end;
    }

    /** {@code 0x1F} or {@code 0b101}. */
    private boolean isRadixLiteral(int start, int end) {
        if (end - start < 3 || sql.charAt(start) != '0') {
            return false;
        }
        char radix = sql.charAt(start + 1);
        for (int i = start + 2; i < end; i++) {
            char c = sql.charAt(i);
            boolean valid = (radix == 'x' || radix == 'X') ? Character.digit(c, 16) >= 0
                : (radix == 'b' || radix == 'B') && (c == '0' || c == '1');
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    private boolean precededByIdentifierPart(int i) {
        return i > 0 && isIdentifierPart(sql.charAt(i - 1));
    }

    /**
     * For a {@code $} at {@code i}, the index of the {@code $} that ends a dollar-quote tag
     * ({@code $$} or {@code $tag$}), or -1 when this is not an opening dollar quote.
     */
    private int dollarTagEnd(int i) {
        int j = i + 1;
        if (j < length && sql.charAt(j) == '$') {
            return j;
        }
        if (j >= length || !(Character.isLetter(sql.charAt(j)) || sql.charAt(j) == '_')) {
            return -1;
        }
        j++;
        while (j < length) {
            char c = sql.charAt(j);
            if (c == '$') {
                return j;
            }
            if (!(Character.isLetterOrDigit(c) || c == '_')) {
                return -1;
            }
            j++;
        }
        return -1;
    }

    /**
     * @return the index after the closing tag, or -1 when the string is not terminated
     */
    private int addDollarQuoted(int start) {
        int tagEnd = dollarTagEnd(start);
        String tag = sql.substring(start, tagEnd + 1);
        int close = sql.indexOf(tag, tagEnd + 1);
        if (close < 0) {
            return -1;
        }
        int end = close + tag.length();
        add(Kind.STRING, start, end);
        return end;
    }

    private boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || (c == '$' && !dialect.dollarQuotes());
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /**
     * Whether {@code c} may continue an unquoted identifier.
     */
    static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
}
