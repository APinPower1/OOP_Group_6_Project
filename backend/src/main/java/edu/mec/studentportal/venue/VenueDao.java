package edu.mec.studentportal.venue;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class VenueDao {
    private static final String LIST_VENUES = """
        SELECT id, name FROM venues ORDER BY name
        """;
    private static final String INSERT_VENUE = """
        INSERT INTO venues (name) VALUES (?)
        """;
    private static final String DELETE_VENUE = """
        DELETE FROM venues WHERE id = ?
        """;

    private final JdbcTemplate jdbcTemplate;

    public VenueDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Venue> list() {
        return jdbcTemplate.query(LIST_VENUES, (resultSet, rowNumber) ->
            new Venue(resultSet.getLong("id"), resultSet.getString("name")));
    }

    public Venue insert(String name) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            java.sql.PreparedStatement statement =
                connection.prepareStatement(INSERT_VENUE,
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, name);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("venue insert generated no key");
        }
        return new Venue(key.longValue(), name);
    }

    public int delete(long venueId) {
        return jdbcTemplate.update(DELETE_VENUE, venueId);
    }
}
