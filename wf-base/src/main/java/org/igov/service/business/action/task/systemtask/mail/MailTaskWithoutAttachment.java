package org.igov.service.business.action.task.systemtask.mail;

import org.activiti.engine.delegate.DelegateExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.igov.io.mail.Mail;

/**
 * @author BW
 */
@Component("MailTaskWithoutAttachment")
public class MailTaskWithoutAttachment extends Abstract_MailTaskCustom {
    
    private final static Logger LOG = LoggerFactory.getLogger(MailTaskWithoutAttachment.class);

    //private Expression saAttachmentsForSend;
    @Override
    public void execute(DelegateExecution oExecution) throws Exception {
        try {
            LOG.info("MailTaskWithoutAttachment...");
            Mail oMail = sendToMailFromMongo(oExecution);
            
            LOG.info("MailTaskWithoutAttachment--->>>>>>>>>>>>>>>>>" + oMail.getBody());
            //oMail.send();
            sendMailOfTask(oMail, oExecution);
            LOG.info("MailTaskWithoutAttachment ok!");
        } catch (Exception ex) {
            LOG.error("MailTaskWithoutAttachment: ", ex);
        }
    }
    
}
