package org.graylog2;

import org.apache.log4j.Level;
import org.apache.log4j.MDC;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;
import org.graylog2.log.Log4jVersionChecker;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Map;

public class GelfMessageFactory {
    private static final int MAX_SHORT_MESSAGE_LENGTH = 250;
    private static final String ORIGIN_HOST_KEY = "originHost";
    private static final String LOGGER_NAME = "logger";
    private static final String LOGGER_NDC = "loggerNdc";
    private static final String THREAD_NAME = "thread";
    private static final String JAVA_TIMESTAMP = "timestampMs";
    
    public static final GelfMessage makeMessage(LoggingEvent event, GelfMessageProvider provider) {
        long timeStamp = Log4jVersionChecker.getTimeStamp(event);
        Level level = event.getLevel();

        String renderedMessage = event.getRenderedMessage();
        String shortMessage;

        if (renderedMessage == null) {
            renderedMessage = "";
        }

        if (renderedMessage.length() > MAX_SHORT_MESSAGE_LENGTH) {
            shortMessage = renderedMessage.substring(0, MAX_SHORT_MESSAGE_LENGTH - 1);
        }
        else {
            shortMessage = renderedMessage;
        }

        if (provider.isExtractStacktrace()) {
            ThrowableInformation throwableInformation = event.getThrowableInformation();
            if (throwableInformation != null) {
                renderedMessage += "\n\r" + extractStacktrace(throwableInformation);
            }
        }
        
        GelfMessage gelfMessage;

        if (provider.isIncludeLocationInformation()) {
            LocationInfo locationInformation = event.getLocationInformation();
            String file = locationInformation.getFileName();
            String lineNumber = locationInformation.getLineNumber();

            gelfMessage = new GelfMessage(shortMessage, renderedMessage, timeStamp, String.valueOf(level.getSyslogEquivalent()), lineNumber, file);
        }
        else {
            gelfMessage =  new GelfMessage(shortMessage, renderedMessage, new Date(timeStamp), String.valueOf(level.getSyslogEquivalent()));
        }
        
        if (provider.getOriginHost() != null) {
            gelfMessage.setHost(provider.getOriginHost());
        }

        if (provider.getFacility() != null) {
            gelfMessage.setFacility(provider.getFacility());
        }

        Map<String, String> fields = provider.getFields();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (entry.getKey().equals(ORIGIN_HOST_KEY) && gelfMessage.getHost() == null) {
                gelfMessage.setHost(fields.get(ORIGIN_HOST_KEY));
            } else {
                gelfMessage.addField(entry.getKey(), entry.getValue());
            }
        }

        if (provider.isAddExtendedInformation()) {

            gelfMessage.addField(THREAD_NAME, event.getThreadName());
            gelfMessage.addField(LOGGER_NAME, event.getLoggerName());
            gelfMessage.addField(JAVA_TIMESTAMP, Long.toString(gelfMessage.getJavaTimestamp()));

            // Get MDC and add a GELF field for each key/value pair
            Map<String, Object> mdc = MDC.getContext();

            if(mdc != null) {
                for(Map.Entry<String, Object> entry : mdc.entrySet()) {

                    gelfMessage.addField(entry.getKey(), entry.getValue().toString());
                }
            }

            // Get NDC and add a GELF field
            String ndc = event.getNDC();

            if(ndc != null) {

                gelfMessage.addField(LOGGER_NDC, ndc);
            }
        }

        return gelfMessage;
    }
    
    private static String extractStacktrace(ThrowableInformation throwableInformation) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwableInformation.getThrowable().printStackTrace(pw);
        return sw.toString();
    }
}
