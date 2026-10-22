package edu.mec.studentportal.report;

import edu.mec.studentportal.common.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/admin/reports/participation — Admin-only participation/feedback summary.
 * The security chain already requires ROLE_ADMIN on /api/admin/**; the service re-checks the
 * database state for the acting user.
 */
@RestController
@RequestMapping("/api/admin/reports")
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/participation")
    public ParticipationReportResponse participationReport(Authentication authentication) {
        return reportService.participationReport(CurrentUser.id(authentication));
    }
}
