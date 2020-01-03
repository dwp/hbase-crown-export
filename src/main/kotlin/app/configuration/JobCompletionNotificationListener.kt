package app.configuration

import app.batch.S3StreamingWriter
import app.batch.legacy.DirectoryWriter
import app.batch.legacy.FileSystemWriter
import app.batch.legacy.S3DirectoryWriter
import app.domain.Record
import app.utils.logging.logInfo
import org.slf4j.LoggerFactory
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.listener.JobExecutionListenerSupport
import org.springframework.batch.item.ItemWriter
import org.springframework.stereotype.Component

@Component
class JobCompletionNotificationListener(private val writer: ItemWriter<Record>) : JobExecutionListenerSupport() {

    override fun afterJob(jobExecution: JobExecution) {
        if (writer is S3StreamingWriter) {
            writer.writeOutput()
            logInfo(logger, "Finished job through StreamingWriter", "status", "${jobExecution.status}")
        } else if (writer is DirectoryWriter) {
            writer.writeOutput()
            logInfo(logger, "Finished job through DirectoryWriter", "status", "${jobExecution.status}")
        } else if (writer is S3DirectoryWriter) {
            writer.writeOutput()
            logInfo(logger, "Finished job through S3DirectoryWriter", "status", "${jobExecution.status}")
        } else  if (writer is FileSystemWriter) {
            writer.writeOutput()
            logInfo(logger, "Finished job through FileSystemWriter", "status", "${jobExecution.status}")
        } else {
            logInfo(logger, "Finished job", "status", "${jobExecution.status}")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(JobCompletionNotificationListener::class.java)
    }
}
