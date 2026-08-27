/**
 * Vanilla hooks. A mixin owned by a module checks that module is enabled before it does anything;
 * the two that serve the core rather than a module — the moon override and the pack repository —
 * always apply.
 *
 * <p>Deliberately <em>not</em> {@code @NullMarked}, unlike the rest of the mod. A mixin class is a
 * stub: its {@code @Shadow} fields are never assigned by this source, and its methods are spliced
 * into a vanilla class at load time. Declaring null contracts here would only claim things about
 * code that does not exist yet — the analysis rightly complains that a shadowed field is never
 * initialised. Where a hook genuinely takes a nullable value, the parameter is annotated directly.
 */
package com.overhaul.mixin;
