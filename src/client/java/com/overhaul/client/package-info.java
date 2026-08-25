/**
 * Client-only setup: keybinds and anything that touches rendering.
 *
 * <p>Marked {@link org.jspecify.annotations.NullMarked}: every type here is non-null unless it
 * carries {@link org.jspecify.annotations.Nullable}. Minecraft itself is null-marked, so matching
 * it lets the null analysis check our side properly instead of treating every value we hand over
 * as an unchecked conversion.
 */
@NullMarked
package com.overhaul.client;

import org.jspecify.annotations.NullMarked;
