package app.configuration

import app.batch.DirectoryWriter
import app.batch.FileSystemWriter
import app.batch.S3DirectoryWriter
import app.domain.Record
import org.slf4j.LoggerFactory
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.listener.JobExecutionListenerSupport
import org.springframework.batch.item.ItemWriter
import org.springframework.stereotype.Component

@Component
class JobCompletionNotificationListener(private val writer: ItemWriter<Record>) : JobExecutionListenerSupport() {

    override fun afterJob(jobExecution: JobExecution) {
        if (writer is DirectoryWriter) {
            writer.writeOutput()
            logger.info("Finished through DirectoryWriter, status : '${jobExecution.status}'.")
        } else if (writer is S3DirectoryWriter) {
            writer.writeOutput()
            logger.info("Finished through S3DirectoryWriter, status : '${jobExecution.status}'.")
        } else if (writer is FileSystemWriter) {
            writer.writeOutput()
            logger.info("Finished through FileSystemWriter, status : '${jobExecution.status}'.")
        }
        logger.info("Finished, status: '${jobExecution.status}'.")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(JobCompletionNotificationListener::class.java)
    }
}
