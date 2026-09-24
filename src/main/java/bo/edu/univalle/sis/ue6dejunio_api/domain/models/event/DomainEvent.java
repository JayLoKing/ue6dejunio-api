package bo.edu.univalle.sis.ue6dejunio_api.domain.models.event;

/**
 * Something the domain did, stated as a fact rather than as an instruction.
 *
 * <p>The marker exists so a publisher can accept events and nothing else. What matters about these
 * is what they are not: they carry no behaviour, name no receiver, and know nothing about what
 * happens next. A plan announces that it was handed in; whether that becomes a notification, a row
 * in an audit log, or nothing at all is not the plan's business.
 */
public interface DomainEvent {}
