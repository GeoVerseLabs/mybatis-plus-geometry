package io.github.geoverselabs.mybatis.geometry.interceptor;

import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A mocked JDBC {@link DataSource} that records every SQL string MyBatis prepares and answers
 * queries with an empty result set, so the full MyBatis plugin chain can be exercised without a
 * database.
 */
final class RecordingDataSource {

    private final List<String> preparedSql = new CopyOnWriteArrayList<>();
    private final DataSource dataSource;

    RecordingDataSource() {
        try {
            ResultSetMetaData metaData = mock(ResultSetMetaData.class);
            when(metaData.getColumnCount()).thenReturn(0);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getMetaData()).thenReturn(metaData);
            when(resultSet.next()).thenReturn(false);

            PreparedStatement statement = mock(PreparedStatement.class);
            when(statement.execute()).thenReturn(true);
            when(statement.getResultSet()).thenReturn(resultSet);
            when(statement.getUpdateCount()).thenReturn(-1);

            DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
            when(databaseMetaData.supportsMultipleResultSets()).thenReturn(false);

            Answer<Object> connectionAnswer = invocation -> {
                String name = invocation.getMethod().getName();
                if (name.equals("prepareStatement")) {
                    preparedSql.add(invocation.getArgument(0));
                    return statement;
                }
                if (name.equals("getMetaData")) {
                    return databaseMetaData;
                }
                return Mockito.RETURNS_DEFAULTS.answer(invocation);
            };
            Connection connection = mock(Connection.class, connectionAnswer);
            when(statement.getConnection()).thenReturn(connection);

            dataSource = mock(DataSource.class);
            when(dataSource.getConnection()).thenReturn(connection);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    DataSource dataSource() {
        return dataSource;
    }

    /** Every SQL string passed to {@code Connection.prepareStatement}, in order. */
    List<String> preparedSql() {
        return preparedSql;
    }

    /** The most recently prepared SQL. */
    String lastSql() {
        if (preparedSql.isEmpty()) {
            throw new AssertionError("no statement was prepared");
        }
        return preparedSql.get(preparedSql.size() - 1);
    }

    void clear() {
        preparedSql.clear();
    }
}
