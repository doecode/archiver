/*
 */
package gov.osti.archiver;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import java.io.IOException;

/**
 * The ERROR MESSAGE content from GitLab API response.
 * 
 * @author ensornl
 */
public class ErrorMessage {
    private Message message;

    // Jackson object mapper
    private static final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    
    /**
     * Convert JSON String to an Object.
     * 
     * @param json the JSON to read
     * @return a Message from that JSON
     * @throws IOException on read errors
     */
    public static ErrorMessage fromJson(String json) throws IOException {
        return mapper.readValue(json, ErrorMessage.class);
    }
    
    /**
     * Retrieve the first or primary error message from the response.
     * 
     * @return an error message if possible.
     */
    public String getErrorMessage() {
        return (null==message || null==message.getBase() || 0==message.getBase().length) ? 
                "No Error Message." : 
                message.getBase()[0];
    }
    
    
    /**
     * @return the message
     */
    public Message getMessage() {
        return message;
    }

    /**
     * @param message the message to set
     */
    public void setMessage(Message message) {
        this.message = message;
    }
}
