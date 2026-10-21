package edu.mec.studentportal.club;

import edu.mec.studentportal.testsupport.DatabaseIntegrationTestSupport;
import edu.mec.studentportal.testsupport.TestAuthSecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** Shared plumbing for club integration tests against real MySQL. */
@Import(TestAuthSecurityConfig.class)
abstract class ClubIntegrationTestSupport extends DatabaseIntegrationTestSupport {
    @Autowired
    ClubService clubService;

    ClubRequest request(String name) {
        return new ClubRequest(name, "A club for testing.", "TECHNICAL");
    }
}
