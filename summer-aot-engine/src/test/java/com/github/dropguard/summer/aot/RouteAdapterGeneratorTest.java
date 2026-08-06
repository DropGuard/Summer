package com.github.dropguard.summer.aot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.RouteInfo;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression: the AOT route adapter must emit a valid resolver-chain call for {@code @Pageable}
 * params. The old format string emitted {@code $T.PAGEABLE} against the new {@code
 * RouteInfoHandlerParam(Class, String, Class, boolean)} signature — the annotation slot is a {@code
 * null} literal for pageable params (resolvers match by type), so it generated {@code
 * null.PAGEABLE} and threw during any AOT build of a controller with a pageable param. No AOT test
 * fixture used {@code @Pageable}, so the break shipped unnoticed.
 */
class RouteAdapterGeneratorTest {

    @TempDir File outputDir;

    @Test
    void pageableParamEmitsValidResolverChainCall() throws Exception {
        BeanDefinition controller =
                new BeanDefinition("com.example.TweetController", "TweetController");
        RouteInfo route =
                new RouteInfo(
                        "GET", "/replies", "com.example.TweetController", "getReplies", "void");
        route.params.add(
                new RouteInfo.ParamInfo(
                        "pageable",
                        "pageable",
                        "com.example.CursorPageable",
                        RouteInfo.ParamBinding.PAGEABLE,
                        false));
        controller.routes.add(route);

        new RouteAdapterGenerator().generate(List.of(controller), outputDir);

        String source =
                Files.readString(
                        outputDir
                                .toPath()
                                .resolve(
                                        "com/github/dropguard/summer/aot/generated/"
                                                + "GeneratedAnnotationRouterAdapter.java"));
        assertTrue(
                source.contains("HttpParameterResolverChain"),
                "must resolve @Pageable through the runtime chain, generated:\n" + source);
        assertTrue(
                source.contains("new RouteInfoHandlerParam"),
                "must build a RouteInfoHandlerParam, generated:\n" + source);
        assertFalse(
                source.contains(".PAGEABLE"),
                "stale ParamBinding reference in generated code:\n" + source);
    }
}
