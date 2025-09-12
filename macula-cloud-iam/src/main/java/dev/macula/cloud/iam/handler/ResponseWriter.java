package dev.macula.cloud.iam.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.macula.boot.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * The type Responser.
 *
 * @author n1
 * @since 2021 /3/26 14:46
 */
@Slf4j
public abstract class ResponseWriter {
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * Write.
     *
     * @param request  the request
     * @param response the response
     * @throws IOException the io exception
     */
    public void write(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            log.debug("Response has already been committed");
            return;
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding("utf-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String resBody = objectMapper.writeValueAsString(this.body(request));
        PrintWriter printWriter = response.getWriter();
        printWriter.print(resBody);
        printWriter.flush();
        printWriter.close();
    }

    /**
     * Msg string.
     *
     * @param request the request
     * @return the string
     */
    protected abstract Result<?> body(HttpServletRequest request);

}
