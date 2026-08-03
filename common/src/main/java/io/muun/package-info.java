/**
 * Default nullability for this module: un-annotated parameters, returns and fields are non-null
 * unless annotated {@code @Nullable}.
 *
 * <p>This makes IntelliJ's nullability analysis match NullAway, which already applies this default
 * to {@code AnnotatedPackages=io.muun} (see linters/errorprone/check.gradle). IntelliJ only picks
 * up package defaults from package-info files in the same source root, so each module carries an
 * identical copy of this file. It only affects IDE analysis; enforcement happens at build time via
 * NullAway.
 */
@DefaultQualifier(
        value = Nonnull.class,
        locations = {TypeUseLocation.PARAMETER, TypeUseLocation.RETURN, TypeUseLocation.FIELD}
)
package io.muun;

import org.checkerframework.framework.qual.DefaultQualifier;
import org.checkerframework.framework.qual.TypeUseLocation;

import javax.annotation.Nonnull;
