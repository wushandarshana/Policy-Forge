package com.horizon.archfixtures.controllers.service;

import org.springframework.web.bind.annotation.RestController;

/** VIOLATION: a @RestController outside web. */
@RestController
public class MisplacedController {
}
