package com.embeddedt.mcdevmcp.external;

public class ExternalToolException extends RuntimeException {
    private final String stdout, stderr;

    public ExternalToolException(String message, String stdout, String stderr) {
        super(message);
        this.stdout = stdout;
        this.stderr = stderr;
    }

    public String getStandardOutput() {
        return this.stdout;
    }

    public String getStandardError() {
        return this.stderr;
    }
}
