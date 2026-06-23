/**
 * Firebase Cloud Functions (v2) for Monthly Employee Approval Workflows
 * Uses Node.js, firebase-functions/v2, firebase-admin, and nodemailer.
 */

const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onDocumentUpdated } = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");

// Initialize Firebase Admin SDK
admin.initializeApp();
const db = admin.firestore();

// Configure Transporter for email dispatch (e.g., SMTP or Gmail setup)
// In production, configure sensitive keys using Google Cloud Secrets Manager
const mailTransporter = nodemailer.createTransport({
  service: "gmail",
  auth: {
    user: process.env.SMTP_USER || "organization-noreply@company.com",
    pass: process.env.SMTP_PASS || "mock-app-password-key"
  }
});

// Deep link configurations matching the Flutter app custom schemas
const FLUTTER_DEEP_LINK_BASE = "companyapproval://portal/reports";

/**
 * ============================================================================
 * REQUIREMENT 4: END-OF-MONTH AUTOMATED AGGREGATION SCHEDULED FUNCTION
 * Runs at 11:55 PM on the last days of the month (28, 29, 30, 31) to capture February, 30-day, and 31-day months.
 * ============================================================================
 */
exports.scheduledMonthlyAggregation = onSchedule({
  schedule: "55 23 28-31 * *", // 11:55 PM on the end-of-month dates
  timeZone: "UTC",
  memory: "256MiB",
}, async (event) => {
  const today = new Date();
  
  // Guard clause: run strictly on the *actual* final day of the current month
  const tomorrow = new Date(today);
  tomorrow.setDate(today.getDate() + 1);
  if (tomorrow.getMonth() === today.getMonth()) {
    logger.info("Today is not final day of the month. Skipping compilation run.");
    return null;
  }

  const currentYear = today.getFullYear();
  const currentMonthNum = today.getMonth() + 1; // 1-indexed
  const monthString = `${currentYear}-${currentMonthNum.toString().padStart(2, "0")}`; // e.g., "2026-06"
  
  logger.info(`Starting monthly compilation for period: ${monthString}`);

  try {
    // 1. Grab all registered records from 'employees' collection
    const employeesSnapshot = await db.collection("employees").get();
    if (employeesSnapshot.empty) {
      logger.info("Zero employees registered. Execution terminated.");
      return null;
    }

    // 2. Compute date boundaries for the target month in UTC
    const startOfMonth = new Date(Date.UTC(currentYear, today.getMonth(), 1, 0, 0, 0));
    const endOfMonth = new Date(Date.UTC(currentYear, today.getMonth() + 1, 0, 23, 59, 59));

    const aggregationPromises = employeesSnapshot.docs.map(async (empDoc) => {
      const empData = empDoc.data();
      const employeeId = empDoc.id; // Or empData.employeeId
      const managerEmail = empData.reportingManagerEmail;
      const managerId = empData.reportingManagerId;

      if (!managerEmail) {
        logger.warn(`Employee #${employeeId} is missing a designated reporting manager. Skipping report.`);
        return;
      }

      // 3. Query 'daily_activities' matching this specific worker and calendar month
      const activitiesSnapshot = await db.collection("daily_activities")
        .where("employeeId", "==", employeeId)
        .where("date", ">=", admin.firestore.Timestamp.fromDate(startOfMonth))
        .where("date", "<=", admin.firestore.Timestamp.fromDate(endOfMonth))
        .orderBy("date", "asc")
        .get();

      if (activitiesSnapshot.empty) {
        logger.info(`No activities recorded for Employee #${employeeId} in month ${monthString}. skipping creation.`);
        return;
      }

      // 4. Concatenate activities sequentially into a unified diagnostic logs text block
      let concatenatedActivities = `Monthly Work logs compiled for Employee ID: ${employeeId}\nPeriod: ${monthString}\n\n`;
      activitiesSnapshot.docs.forEach((actDoc, idx) => {
        const actData = actDoc.data();
        const formattedDate = actData.date.toDate().toISOString().split("T")[0];
        concatenatedActivities += `[${formattedDate}] Day ${idx + 1}:\n${actData.activities}\n`;
        concatenatedActivities += `--------------------------------------------------\n`;
      });

      // 5. Create a unified container record in the 'monthly_reports' collection
      const reportDocId = `${employeeId}_${monthString}`;
      const reportRef = db.collection("monthly_reports").doc(reportDocId);

      const reportPayload = {
        employeeId: employeeId,
        monthYear: monthString,
        aggregatedActivities: concatenatedActivities,
        status: "Pending Manager Approval",
        managerId: managerId,
        managerEmail: managerEmail,
        submittedAt: admin.firestore.FieldValue.serverTimestamp(),
      };

      await reportRef.set(reportPayload);
      logger.info(`Generated report ${reportDocId} for employee #${employeeId}`);

      // 6. Deliver deep link to the designated supervisor for direct mobile approval actions
      const deepLink = `${FLUTTER_DEEP_LINK_BASE}?reportId=${reportDocId}`;
      const emailContent = {
        from: '"Company Portal Admin" <organization-noreply@company.com>',
        to: managerEmail,
        subject: `⚠️ Action Required: Review Monthly activities for Employee #${employeeId}`,
        html: `
          <h3>Monthly Activities Approval Requested</h3>
          <p>The daily logs compiled by <b>Employee #${employeeId}</b> for <b>${monthString}</b> have been consolidated and require your approval.</p>
          <hr/>
          <pre style="background: #f4f4f4; padding: 12px; border-radius: 4px;">${concatenatedActivities}</pre>
          <hr/>
          <p>Please perform the action directly on your phone applet using the secure link: </p>
          <p><a href="${deepLink}" style="background-color: #008080; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px;">APPROVE IN APP</a></p>
        `
      };

      await mailTransporter.sendMail(emailContent);
      logger.info(`Dispatched approval review email to manager: ${managerEmail}`);
    });

    await Promise.all(aggregationPromises);
    logger.info("Finished monthly aggregation cron schedule successfully.");
  } catch (error) {
    logger.error("Error executing monthly scheduled aggregation workflow:", error);
  }
});


/**
 * ============================================================================
 * REQUIREMENT 5: MANAGER APPROVAL & ADMIN ESCALATION TRIGGER
 * Triggered on the change of any 'monthly_reports' document.
 * Checks if status went from "Pending Manager Approval" to "Approved by Manager".
 * ============================================================================
 */
exports.onManagerApprovalEscalation = onDocumentUpdated("monthly_reports/{reportId}", async (event) => {
  const beforeData = event.data.before.data();
  const afterData = event.data.after.data();

  // If update was deleted or is missing proper schema variables, terminate
  if (!beforeData || !afterData) {
    return null;
  }

  // Detect state transition edge: "Pending Manager Approval" -> "Approved by Manager"
  const oldStatus = beforeData.status;
  const newStatus = afterData.status;

  if (oldStatus === "Pending Manager Approval" && newStatus === "Approved by Manager") {
    logger.info(`Workflow state transition matching '${event.params.reportId}' triggered. Escalating.`);

    const employeeId = afterData.employeeId;
    const monthYear = afterData.monthYear;
    const aggregatedData = afterData.aggregatedActivities;
    const managerId = afterData.managerId;

    const adminEmail = "admin-team@company.com"; // System Admin static distribution address

    try {
      // 1. Immediately transit the report status database state to "Sent to Admin"
      await event.data.after.ref.update({
        status: "Sent to Admin",
        sentToAdminAt: admin.firestore.FieldValue.serverTimestamp()
      });
      logger.info(`Document status safely updated to 'Sent to Admin' for report: ${event.params.reportId}`);

      // 2. Draft and post an administrative warning containing the validated logs
      const adminEmailPayload = {
        from: '"Company Portal Workflows" <organization-noreply@company.com>',
        to: adminEmail,
        subject: `✅ Approved Report: Employee #${employeeId} (${monthYear})`,
        html: `
          <h2>Monthly Report Approved By Supervisor</h2>
          <p>The consolidated monthly report for <b>Employee #${employeeId}</b> has been officially approved by Reporting Manager <b>#${managerId}</b>.</p>
          <p><b>Period:</b> ${monthYear}</p>
          <p><b>Status:</b> Escallated & Sent to Admin - Awaiting final payout / bookkeeping ledger entry.</p>
          <hr/>
          <h3>Aggregated Activities Log:</h3>
          <pre style="background: #eef; padding: 15px; border-radius: 6px; border: 1px solid #ccc; font-family: monospace;">${aggregatedData}</pre>
          <hr/>
          <p>Open the system portal dashboard to process paymaster finalization:</p>
          <p><a href="${FLUTTER_DEEP_LINK_BASE}?reportId=${event.params.reportId}" style="background-color: #337ab7; color: white; padding: 12px 24px; text-decoration: none; border-radius: 5px; font-weight: bold;">VIEW IN SECURE PORTAL</a></p>
        `
      };

      await mailTransporter.sendMail(adminEmailPayload);
      logger.info(`Successfully escalated approval notification dispatch to Admin: ${adminEmail}`);

    } catch (err) {
      logger.error(`Critical escalation workflow failure on report ${event.params.reportId}:`, err);
    }
  }

  return null;
});
