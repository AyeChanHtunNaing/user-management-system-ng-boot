package dev.peacechan.usermanagement.filter;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.peacechan.usermanagement.constant.SecurityConstant;
import dev.peacechan.usermanagement.domain.HttpResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;


import java.io.IOException;
import java.io.OutputStream;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        HttpResponse httpResponse = new HttpResponse( UNAUTHORIZED.value(), UNAUTHORIZED, UNAUTHORIZED.getReasonPhrase(), SecurityConstant.ACCESS_DENIED_MESSAGE);
        response.setStatus(UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ObjectMapper mapper = new ObjectMapper();
        OutputStream outputStream = response.getOutputStream();
        mapper.writeValue(outputStream, httpResponse);
        outputStream.flush();
    }
}