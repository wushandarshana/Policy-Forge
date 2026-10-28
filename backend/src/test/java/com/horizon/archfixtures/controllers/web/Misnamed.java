package com.horizon.archfixtures.controllers.web;

import org.springframework.web.bind.annotation.RestController;

/** VIOLATION: a @RestController in web whose name does not end in Controller. */
@RestController
public class Misnamed {
}
