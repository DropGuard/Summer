package com.github.dropguard.summer.core.exception;

import com.github.dropguard.summer.core.ErrorCode;

/**
 * Thrown when a {@code @ConfigurationProperties} record has a field that is
 * absent from the YAML configuration and has no {@code @DefaultValue}.
 */
public class MissingFieldException extends ConfigurationException {

	private final String fieldName;
	private final String recordName;

	public MissingFieldException(String fieldName, String recordName, String message) {
		super(ErrorCode.CONFIG_MISSING_FIELD, message);
		this.fieldName = fieldName;
		this.recordName = recordName;
	}

	public String getFieldName() {
		return fieldName;
	}

	public String getRecordName() {
		return recordName;
	}
}
