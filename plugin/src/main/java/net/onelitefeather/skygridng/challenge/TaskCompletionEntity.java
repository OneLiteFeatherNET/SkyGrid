package net.onelitefeather.skygridng.challenge;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

/**
 * JPA entity mapping of the {@code task_completion} table (schema owned by the Flyway migrations, never
 * altered by Hibernate). Row existence alone is the completion fact: no status or boolean column.
 *
 * <p>Deliberately not a record: Hibernate needs a mutable, proxyable class. The protected no-arg
 * constructor exists for Hibernate only.
 */
@Entity
@Table(name = "task_completion")
@NamedQueries({
        @NamedQuery(
                name = "TaskCompletionEntity.ofPlayer",
                query = "select e from TaskCompletionEntity e where e.id.playerId = :playerId"),
        @NamedQuery(
                name = "TaskCompletionEntity.deleteBySeason",
                query = "delete from TaskCompletionEntity e where e.id.seasonId = :seasonId")
})
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
