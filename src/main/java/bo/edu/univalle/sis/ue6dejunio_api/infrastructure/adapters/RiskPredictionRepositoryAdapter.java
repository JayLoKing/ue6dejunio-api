package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.CourseStudentRisk;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.NewRiskPrediction;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskLevel;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskPrediction;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.StudentRisk;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskPredictionDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.RiskPredictionEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaRiskPredictionRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class RiskPredictionRepositoryAdapter implements IRiskPredictionDomain {

    private final JpaRiskPredictionRepository repository;

    /**
     * Turns the feature vector into the text the {@code jsonb} column holds. Here and not in the
     * service: which format the inputs are kept in is a storage decision, and a use case that has
     * to serialize before it can save has taken on a concern it does not have an opinion about.
     */
    private final JsonMapper json;

    public RiskPredictionRepositoryAdapter(
            JpaRiskPredictionRepository repository, JsonMapper json) {
        this.repository = repository;
        this.json = json;
    }

    /**
     * One read and one write for a whole run.
     *
     * <p>The read is what makes the transition detectable: the row is about to be overwritten, so
     * the level it held has to be picked up in the same transaction that replaces it. Doing it
     * inside the write also settles the race — two runs cannot both read the same "before" and both
     * report the same change, because the second one waits for the first to commit.
     */
    @Override
    @Transactional
    public List<UpsertResult> upsertAll(List<NewRiskPrediction> predictions) {
        if (predictions.isEmpty()) {
            return List.of();
        }

        Map<Key, RiskPredictionEntity> existing = loadExisting(predictions);

        List<RiskPredictionEntity> toSave = new ArrayList<>(predictions.size());
        List<RiskLevel> previousLevels = new ArrayList<>(predictions.size());

        for (NewRiskPrediction prediction : predictions) {
            RiskPredictionEntity entity = existing.get(Key.of(prediction));
            if (entity == null) {
                entity = new RiskPredictionEntity();
                entity.setStudentId(prediction.studentId());
                entity.setClassGroupId(prediction.classGroupId());
                entity.setTrimester(prediction.trimester());
                // A fresh prediction is nobody's business yet. Only the row being replaced can
                // carry
                // an "attended" the Director set, and that is preserved below rather than reset.
                entity.setAttended(false);
                previousLevels.add(null);
            } else {
                previousLevels.add(levelOf(entity));
            }
            entity.setRiskLevel(prediction.riskLevel().modelName());
            entity.setPFail(prediction.pFail());
            entity.setPOutstanding(prediction.pOutstanding());
            // Not guarded. A vector that cannot be written down is a prediction nobody can ever
            // explain, and storing "{}" in its place would leave a row that looks complete and says
            // nothing — a failure that surfaces months later as an empty panel.
            entity.setFeaturesAnalyzed(json.writeValueAsString(prediction.features()));
            entity.setPredictedAt(prediction.predictedAt());
            toSave.add(entity);
        }

        List<RiskPredictionEntity> saved = repository.saveAll(toSave);

        List<UpsertResult> results = new ArrayList<>(saved.size());
        for (int i = 0; i < saved.size(); i++) {
            results.add(new UpsertResult(toDomain(saved.get(i)), previousLevels.get(i)));
        }
        return results;
    }

    /**
     * Every row this batch might be replacing, in one query.
     *
     * <p>Grouped by trimester because a run is normally one trimester and the query takes a single
     * value for it. A batch spanning two costs two reads, not two hundred.
     */
    private Map<Key, RiskPredictionEntity> loadExisting(List<NewRiskPrediction> predictions) {
        Map<Integer, Set<UUID>> studentsByTrimester = new HashMap<>();
        Map<Integer, Set<UUID>> groupsByTrimester = new HashMap<>();
        for (NewRiskPrediction p : predictions) {
            studentsByTrimester
                    .computeIfAbsent(p.trimester(), t -> new LinkedHashSet<>())
                    .add(p.studentId());
            groupsByTrimester
                    .computeIfAbsent(p.trimester(), t -> new LinkedHashSet<>())
                    .add(p.classGroupId());
        }

        Map<Key, RiskPredictionEntity> existing = new HashMap<>();
        for (Map.Entry<Integer, Set<UUID>> entry : studentsByTrimester.entrySet()) {
            Integer trimester = entry.getKey();
            List<RiskPredictionEntity> rows =
                    repository.findByTrimesterAndClassGroupIdInAndStudentIdIn(
                            trimester, groupsByTrimester.get(trimester), entry.getValue());
            for (RiskPredictionEntity row : rows) {
                existing.put(Key.of(row), row);
            }
        }
        return existing;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentRisk> byIds(Collection<UUID> predictionIds) {
        if (predictionIds == null || predictionIds.isEmpty()) {
            return List.of();
        }
        return toViews(repository.findByIdsWithNames(predictionIds));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Stamp first, then read back what carries this run's instant. The other order — read who is
     * un-announced, then stamp them — leaves both of two overlapping runs holding the same list and
     * writing to the same teacher.
     *
     * <p>{@code flushAutomatically} and {@code clearAutomatically} are not decoration on a
     * {@code @Modifying} query: without the flush a pending write could be applied after the bulk
     * update and undo the stamp, and without the clear the entities already in the persistence
     * context would still hold the previous value.
     */
    @Override
    @Transactional
    public Set<UUID> claimForNotification(
            Collection<UUID> predictionIds, LocalDateTime now, LocalDateTime notBefore) {
        if (predictionIds == null || predictionIds.isEmpty()) {
            return Set.of();
        }
        repository.claimForNotification(predictionIds, now, notBefore);
        return Set.copyOf(repository.findIdsNotifiedAt(predictionIds, now));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentRisk> byClassGroupAndTrimester(UUID classGroupId, int trimester) {
        return toViews(repository.findByClassGroupWithNames(classGroupId, trimester));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentRisk> byCourseAndTrimester(UUID courseId, int trimester) {
        return toViews(repository.findByCourseWithNames(courseId, trimester));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseStudentRisk> byAcademicYearAndTrimester(
            Integer academicYearId, int trimester) {
        List<Object[]> rows = repository.findByAcademicYearWithNames(academicYearId, trimester);
        List<CourseStudentRisk> views = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            views.add(
                    new CourseStudentRisk(
                            (UUID) row[0],
                            new StudentRisk(
                                    toDomain((RiskPredictionEntity) row[1]),
                                    (String) row[2],
                                    (String) row[3],
                                    (String) row[4])));
        }
        return views;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentRisk> byStudent(UUID studentId) {
        return toViews(repository.findByStudentWithNames(studentId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RiskPrediction> findById(UUID id) {
        return repository.findById(id).map(RiskPredictionRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional
    public RiskPrediction markAttended(UUID id, boolean attended) {
        RiskPredictionEntity entity =
                repository
                        .findById(id)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Predicción de riesgo", id));
        entity.setAttended(attended);
        return toDomain(repository.save(entity));
    }

    private static List<StudentRisk> toViews(List<Object[]> rows) {
        List<StudentRisk> views = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            views.add(
                    new StudentRisk(
                            toDomain((RiskPredictionEntity) row[0]),
                            (String) row[1],
                            (String) row[2],
                            (String) row[3]));
        }
        return views;
    }

    private static RiskPrediction toDomain(RiskPredictionEntity entity) {
        return new RiskPrediction(
                entity.getId(),
                entity.getStudentId(),
                entity.getClassGroupId(),
                entity.getTrimester(),
                levelOf(entity),
                entity.getPFail(),
                entity.getPOutstanding(),
                entity.isAttended(),
                entity.getFeaturesAnalyzed(),
                entity.getPredictedAt());
    }

    /**
     * A stored level the enum does not know is a bug here, not bad input: the column carries a
     * CHECK listing the same four words. Failing loudly beats handing back a null that the
     * transition check would read as "never predicted before" and announce all over again.
     *
     * <p>Left as an {@link IllegalStateException}, and therefore a 500, deliberately. It is the
     * honest answer: the row disagrees with its own constraint, so this system is broken and no
     * amount of retrying will change that. A 503 would tell the Director to come back later about a
     * row that will still be wrong tomorrow, and the domain exception that maps to it says "the
     * model could not be asked", which is not what happened.
     */
    private static RiskLevel levelOf(RiskPredictionEntity entity) {
        return RiskLevel.fromModel(entity.getRiskLevel())
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "The prediction "
                                                + entity.getId()
                                                + " holds an unknown risk level: "
                                                + entity.getRiskLevel()));
    }

    /** What {@code uq_risk_pred} makes unique, so the lookup matches the constraint exactly. */
    private record Key(UUID studentId, UUID classGroupId, Integer trimester) {

        static Key of(NewRiskPrediction prediction) {
            return new Key(
                    prediction.studentId(), prediction.classGroupId(), prediction.trimester());
        }

        static Key of(RiskPredictionEntity entity) {
            return new Key(entity.getStudentId(), entity.getClassGroupId(), entity.getTrimester());
        }
    }
}
