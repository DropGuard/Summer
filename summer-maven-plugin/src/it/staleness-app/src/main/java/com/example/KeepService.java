package com.example;

import com.github.dropguard.summer.core.Component;

/** Always present in both builds — sanity baseline. */
@Component
public class KeepService {

    public String tag() {
        return "keep";
    }
}
