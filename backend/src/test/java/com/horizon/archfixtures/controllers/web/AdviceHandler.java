package com.horizon.archfixtures.controllers.web;

import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Allowed: a controller advice is not a controller, so its name need not end in Controller. */
@RestControllerAdvice
public class AdviceHandler {
}
