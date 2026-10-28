package com.horizon.archfixtures.controllers.repository;

import org.springframework.stereotype.Controller;

/** VIOLATION: a plain @Controller outside web. */
@Controller
public class MisplacedPlainController {
}
