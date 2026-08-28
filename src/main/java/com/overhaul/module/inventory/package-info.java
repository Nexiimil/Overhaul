/**
 * Handling items in bulk: quick-stacking, sorting, locked slots, a trash target, and opening a
 * container item where it sits.
 *
 * <p>Marked {@link org.jspecify.annotations.NullMarked}: every type here is non-null unless it
 * carries {@link org.jspecify.annotations.Nullable}. Minecraft itself is null-marked, so matching
 * it lets the null analysis check our side properly instead of treating every value we hand over
 * as an unchecked conversion.
 */
@NullMarked
package com.overhaul.module.inventory;

import org.jspecify.annotations.NullMarked;
