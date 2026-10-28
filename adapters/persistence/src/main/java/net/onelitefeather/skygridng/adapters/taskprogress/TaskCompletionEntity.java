package net.onelitefeather.skygridng.adapters.taskprogress;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * JPA entity mapping of the {@code task_completion} table (schema owned by
 * {@code V1__create_task_completion.sql}, never altered by Hibernate: see {@code persistence.xml}'s
 * {@code hibernate.hbm2ddl.auto=validate}).
 *
 * <p>Deliberately not a record, unlike every other value type in this project: Hibernate needs a
 * mutable, proxyable class for its dirty-checking and (potentially) lazy-loading machinery, which a
 * record's all-final-fields, no-arg-constructor-free shape cannot provide. This is a documented,
 * narrow exception to the project's "everything is a record" house style, confined to this one JPA
 * entity class; the composite id ({@link TaskCompletionId}) and every other type in this module stay
 * immutable records. The protected no-arg constructor exists for Hibernate only and must never be
 * called from application code, which always uses the public constructor.
 *
 * <p>No status or boolean column: row existence alone is the completion fact (see
 * {@code V1__create_task_completion.sql}'s data minimization comment), so unlike
 * {@code ChallengeTierEntity}'s {@code tier} field, there is nothing else here to store or mutate.
 */
@Entity
@Table(name = "task_completion")
public class TaskCompletionEntity {

    @EmbeddedId
    private TaskCompletionId id;

    /** For Hibernate's proxy and reflection machinery only; never call this from application code. */
    protected TaskCompletionEntity() {
    }

    public TaskCompletionEntity(TaskCompletionId id) {
        this.id = id;
    }

    public TaskCompletionId getId() {
        return id;
    }
}
