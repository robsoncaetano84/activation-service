package com.robson.activation.api;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;

final class ApiProblems {

    private static final String BASE_URI = "https://api.exemplo.com/problems/";

    private ApiProblems() {
    }

    static ProblemDetail of(HttpStatusCode status, String type, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(BASE_URI + type));
        problem.setTitle(title);
        return problem;
    }
}
