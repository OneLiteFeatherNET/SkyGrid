package net.onelitefeather.skygridng.adapters.command.handler;

/**
 * Marker for a class whose methods are Cloud {@code @Command}-annotated subcommand handlers.
 *
 * Each implementation is bound into a Guice set binder (see {@code CommandModule}) and handed to
 * {@code AnnotationParser} by {@code CloudCommandRegistrar}. Adding a new subcommand group is one
 * new class plus one binding line, not a new constructor parameter here or in
 * {@code CloudCommandRegistrar#register}.
 */
public interface CloudCommandHandler {
}
