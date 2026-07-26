package com.github.dropguard.summer.web;

import java.util.HashMap;
import java.util.Map;

class Response {
	HttpStatus status;
	byte[] body;
	Object resultObject;
	BodyConverter converter;
	final Map<String, String> headers = new HashMap<>();
}
