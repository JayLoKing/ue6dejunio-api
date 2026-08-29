package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.institution;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.institution.Institution;

public interface IInstitutionDomain {

    /** The school as it stands right now, Director included. */
    Institution current();
}
