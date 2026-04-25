package com.lobox.interview.common;

import java.io.Serializable;

public record GeneralApiErrorResponse(String error) implements Serializable {
}
