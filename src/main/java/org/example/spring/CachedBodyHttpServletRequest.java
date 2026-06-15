package org.example.spring;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.body = request.getInputStream().readAllBytes();
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream stream = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            public int read() { return stream.read(); }
            public boolean isFinished() { return stream.available() == 0; }
            public boolean isReady() { return true; }
            public void setReadListener(ReadListener l) {}
        };
    }

    public byte[] getBody() { return body; }
}