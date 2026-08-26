package com.github.dropguard.summer.fixtures.aop.inherited;

import com.github.dropguard.summer.fixtures.aop.Logged;

/** Contract carried by an abstract base — the child never declares this interface itself. */
public interface EchoApi {

    @Logged
    String echo(String in);
}
