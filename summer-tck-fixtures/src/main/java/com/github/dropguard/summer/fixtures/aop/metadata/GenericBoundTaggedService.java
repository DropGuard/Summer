package com.github.dropguard.summer.fixtures.aop.metadata;

import java.util.List;

/**
 * Type-variable parameter rendering contract for the overload-exact binding key. Every parameter
 * shape below must render identically on both engines — Runtime derives the key from the erased
 * parameter ({@code Class#getName()}) while enrichment/AOT derive it from Jandex {@code
 * Type#name()} — because a type variable's erasure is its leftmost bound (JLS 4.6), which is
 * exactly what Jandex {@code TypeVariable#name()} returns ({@code bounds[0]}). Pinned cell by cell,
 * dual-engine, by GenericBoundTaggedServiceTest:
 *
 * <ul>
 *   <li>bounded variable {@code T extends Number} -> {@code java.lang.Number}
 *   <li>parameterized {@code List<T>} -> raw {@code java.util.List}
 *   <li>array of a variable, any dimension {@code T[]}/{@code T[][]} -> {@code [Ljava.lang.Number;}
 *       / {@code [[Ljava.lang.Number;}
 *   <li>multiple bounds {@code T extends Number & Comparable<T>} -> leftmost {@code
 *       java.lang.Number}
 *   <li>self-referencing bound {@code U extends Comparable<U>} -> raw {@code java.lang.Comparable}
 * </ul>
 */
public interface GenericBoundTaggedService<T extends Number> {

    @MetadataTagged
    String save(T value);

    String delete(T value);

    @MetadataTagged
    String saveAll(List<T> values);

    @MetadataTagged
    String saveAll(T[] values);

    @MetadataTagged
    String saveAll(T[][] values);

    @MetadataTagged
    <U extends Comparable<U>> String rank(U value);
}
