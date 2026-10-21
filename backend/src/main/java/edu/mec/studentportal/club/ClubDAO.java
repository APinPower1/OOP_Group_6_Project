package edu.mec.studentportal.club;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** Parameterized SQL for clubs and memberships. */
@Repository
class ClubDAO {
    private static final String CLUB_COLUMNS =
        "id, name, description, category, is_active, created_at, updated_at";
    private static final String MEMBERSHIP_COLUMNS =
        "id, club_id, student_id, joined_at, status";

    private static final RowMapper<Club> CLUB_ROW_MAPPER = (rs, rowNum) -> new Club(
        rs.getLong("id"),
        rs.getString("name"),
        rs.getString("description"),
        rs.getString("category"),
        rs.getBoolean("is_active"),
        rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getTimestamp("updated_at").toLocalDateTime()
    );

    private static final RowMapper<Membership> MEMBERSHIP_ROW_MAPPER = (rs, rowNum) -> new Membership(
        rs.getLong("id"),
        rs.getLong("club_id"),
        rs.getLong("student_id"),
        rs.getTimestamp("joined_at").toLocalDateTime(),
        MembershipStatus.valueOf(rs.getString("status"))
    );

    private static final RowMapper<ActorRow> ACTOR_ROW_MAPPER = (rs, rowNum) ->
        new ActorRow(rs.getLong("id"), rs.getString("role"), rs.getBoolean("enabled"));

    private static final RowMapper<MemberRow> MEMBER_ROW_MAPPER = (rs, rowNum) -> new MemberRow(
        rs.getLong("id"),
        rs.getLong("student_id"),
        rs.getString("full_name"),
        rs.getString("roll_number"),
        MembershipStatus.valueOf(rs.getString("status")),
        rs.getTimestamp("joined_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    ClubDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    long insertClub(String name, String description, String category) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO clubs (name, description, category, is_active) VALUES (?, ?, ?, TRUE)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, name);
            statement.setString(2, description);
            statement.setString(3, category);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Club insert did not return a generated id");
        }
        return key.longValue();
    }

    Optional<Club> findById(long clubId) {
        List<Club> rows = jdbcTemplate.query(
            "SELECT " + CLUB_COLUMNS + " FROM clubs WHERE id = ?",
            CLUB_ROW_MAPPER,
            clubId
        );
        return rows.stream().findFirst();
    }

    /** Locks the club row for the rest of the transaction (membership joins serialize on it). */
    Optional<Club> lockById(long clubId) {
        List<Club> rows = jdbcTemplate.query(
            "SELECT " + CLUB_COLUMNS + " FROM clubs WHERE id = ? FOR UPDATE",
            CLUB_ROW_MAPPER,
            clubId
        );
        return rows.stream().findFirst();
    }

    int updateClub(long clubId, String name, String description, String category) {
        return jdbcTemplate.update(
            "UPDATE clubs SET name = ?, description = ?, category = ? WHERE id = ?",
            name,
            description,
            category,
            clubId
        );
    }

    int setActive(long clubId, boolean active) {
        return jdbcTemplate.update(
            "UPDATE clubs SET is_active = ? WHERE id = ?",
            active,
            clubId
        );
    }

    boolean nameExists(String name, Long excludeClubId) {
        Integer count;
        if (excludeClubId == null) {
            count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM clubs WHERE name = ?",
                Integer.class,
                name
            );
        } else {
            count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM clubs WHERE name = ? AND id <> ?",
                Integer.class,
                name,
                excludeClubId
            );
        }
        return count != null && count > 0;
    }

    List<Club> findActive() {
        return jdbcTemplate.query(
            "SELECT " + CLUB_COLUMNS + " FROM clubs WHERE is_active = TRUE ORDER BY name, id",
            CLUB_ROW_MAPPER
        );
    }

    int countActiveMembers(long clubId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM club_members WHERE club_id = ? AND status = 'ACTIVE'",
            Integer.class,
            clubId
        );
        return count == null ? 0 : count;
    }

    Optional<Membership> findMembership(long clubId, long studentId) {
        List<Membership> rows = jdbcTemplate.query(
            "SELECT " + MEMBERSHIP_COLUMNS + " FROM club_members WHERE club_id = ? AND student_id = ?",
            MEMBERSHIP_ROW_MAPPER,
            clubId,
            studentId
        );
        return rows.stream().findFirst();
    }

    long insertMembership(long clubId, long studentId, LocalDateTime joinedAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO club_members (club_id, student_id, joined_at, status) VALUES (?, ?, ?, 'ACTIVE')",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, clubId);
            statement.setLong(2, studentId);
            statement.setTimestamp(3, Timestamp.valueOf(joinedAt));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Membership insert did not return a generated id");
        }
        return key.longValue();
    }

    int reactivateMembership(long membershipId, LocalDateTime joinedAt) {
        return jdbcTemplate.update(
            "UPDATE club_members SET status = 'ACTIVE', joined_at = ? WHERE id = ?",
            Timestamp.valueOf(joinedAt),
            membershipId
        );
    }

    int leaveMembership(long membershipId) {
        return jdbcTemplate.update(
            "UPDATE club_members SET status = 'LEFT' WHERE id = ?",
            membershipId
        );
    }

    List<MemberRow> findMembers(long clubId) {
        return jdbcTemplate.query(
            "SELECT cm.id, cm.student_id, u.full_name, sp.roll_number, cm.status, cm.joined_at "
                + "FROM club_members cm "
                + "JOIN users u ON u.id = cm.student_id "
                + "JOIN student_profiles sp ON sp.user_id = cm.student_id "
                + "WHERE cm.club_id = ? ORDER BY cm.id",
            MEMBER_ROW_MAPPER,
            clubId
        );
    }

    Optional<ActorRow> findActor(long userId) {
        List<ActorRow> rows = jdbcTemplate.query(
            "SELECT id, role, enabled FROM users WHERE id = ?",
            ACTOR_ROW_MAPPER,
            userId
        );
        return rows.stream().findFirst();
    }

    record ActorRow(long id, String role, boolean enabled) {}

    record MemberRow(
        long membershipId,
        long studentId,
        String fullName,
        String rollNumber,
        MembershipStatus status,
        LocalDateTime joinedAt
    ) {}
}
