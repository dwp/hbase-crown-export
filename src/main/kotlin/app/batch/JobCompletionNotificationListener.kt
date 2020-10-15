package app.batch

import app.services.ExportStatusService
import org.apache.hadoop.hbase.TableNotEnabledException
import org.apache.hadoop.hbase.TableNotFoundException
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.listener.JobExecutionListenerSupport
import org.springframework.stereotype.Component
import uk.gov.dwp.dataworks.logging.DataworksLogger

@Component
class JobCompletionNotificationListener(private val exportStatusService: ExportStatusService) :
        JobExecutionListenerSupport() {

    override fun afterJob(jobExecution: JobExecution) {
        logger.info("Job completed", "exit_status" to jobExecution.exitStatus.exitCode)
        if (jobExecution.exitStatus.equals(ExitStatus.COMPLETED)) {
            exportStatusService.setExportedStatus()
        }
        else {
            if (isATableUnavailableExceptions(jobExecution.allFailureExceptions)) {
                logger.error("Setting table unavailable status",
                        "job_exit_status" to "${jobExecution.exitStatus}")
                exportStatusService.setTableUnavailableStatus()
            } else {
                logger.error("Setting export failed status",
                        "job_exit_status" to "${jobExecution.exitStatus}")
                exportStatusService.setFailedStatus()
            }
        }
    }

    private fun isATableUnavailableExceptions(allFailureExceptions: MutableList<Throwable>) : Boolean {
        logger.info("Checking if table is unavailable exception",
                "failure_exceptions" to allFailureExceptions.size.toString())
        allFailureExceptions.forEach {
            logger.info("Checking current failure exception",
                    "failure_exception" to it.localizedMessage,
                    "cause" to it.cause.toString(),
                    "cause_message" to (it.message ?: ""))
            if (it.cause is TableNotFoundException || it.cause is TableNotEnabledException) {
                return true
            }
        }
        return false
    }

    companion object {
        val logger = DataworksLogger.getLogger(S3StreamingWriter::class.toString())
    }
}
