-- =============================================================================
-- RESET PARA PRUEBAS MANUALES  —  BORRA DATOS. NO ES UNA MIGRACION.
-- =============================================================================
--
-- No lleva prefijo V porque no describe el esquema: no crea ni altera nada,
-- solo vacia. Se corre a mano, sobre una base que se puede perder.
--
--   >>> ESTO BORRA TODOS LOS USUARIOS, CURSOS, ESTUDIANTES, NOTAS,       <<<
--   >>> ASISTENCIAS, PLANES PDC Y NOTIFICACIONES. NO SE PUEDE DESHACER.  <<<
--   >>> HACER UN DUMP ANTES SI HAY ALGO QUE IMPORTE:                     <<<
--   >>>   pg_dump -U postgres ue_6_de_junio > respaldo.sql               <<<
--
-- -----------------------------------------------------------------------------
-- QUE SOBREVIVE, Y POR QUE
-- -----------------------------------------------------------------------------
--   roles            El arranque no puede recrear al Director sin el rol
--                    "Director"; falla con "Rol Director no existe en la BDD".
--   knowledge_areas  subjects.id_area es NOT NULL y no hay pantalla para crear
--                    areas: sin ellas el Director no podria crear materias.
--   academic_years   La gestion. El arranque la recrea igual, pero borrarla
--                    obligaria a reiniciar antes de poder crear un curso.
--
-- Todo lo demas se borra, incluido el catalogo academico (niveles, grados,
-- paralelos, materias, trimestres), para poder probar las pantallas de
-- administracion del Director desde cero.
--
-- -----------------------------------------------------------------------------
-- ANTES DE CORRER: EL DIRECTOR
-- -----------------------------------------------------------------------------
-- Esto borra la tabla users, incluido el Director. La aplicacion lo vuelve a
-- crear al arrancar, pero SOLO si estas variables estan en el .env de la API.
-- Ya no hay valores por defecto en el codigo: sin ellas no se crea nadie y
-- nadie puede entrar al sistema.
--
--   BOOTSTRAP_DIRECTOR_EMAIL=
--   BOOTSTRAP_DIRECTOR_PASSWORD=
--   BOOTSTRAP_DIRECTOR_CI=
--   BOOTSTRAP_DIRECTOR_NAMES=
--   BOOTSTRAP_DIRECTOR_LAST_NAMES=
--
-- La cuenta se crea exigiendo cambio de contrasena en el primer ingreso.
--
-- -----------------------------------------------------------------------------
-- ORDEN DE TRABAJO
-- -----------------------------------------------------------------------------
--   1. Parar la API.
--   2. Confirmar las variables de arriba en el .env.
--   3. Correr este script.
--   4. Levantar la API  -> recrea al Director y la gestion actual.
--   5. Entrar como Director y cambiar la contrasena.
--   6. Cargar en este orden, que es el de las dependencias:
--        Niveles -> Grados -> Paralelos -> Materias -> Trimestres
--        Usuarios (Secretario, docente de aula, docente tecnico)
--        Cursos (grado + paralelo + docente de aula + materias)
--        Estudiantes (nomina PDF o alta manual)
-- =============================================================================

BEGIN;

-- Una sola sentencia: TRUNCATE resuelve el orden de las claves foraneas entre
-- las tablas nombradas, cosa que una lista de DELETE tendria que acertar a mano.
-- RESTART IDENTITY devuelve los seriales a 1, asi los grados y paralelos nuevos
-- arrancan numerados desde el principio.
--
-- CASCADE alcanza a cualquier tabla que apunte a estas. Las tres que se
-- conservan no apuntan a ninguna de la lista — son ellas las apuntadas — asi
-- que quedan intactas.
TRUNCATE TABLE
    notifications,
    risk_predictions,
    attendance,
    academic_scores,
    assessment_scores,
    assessment_events,
    evaluation_criteria,
    curriculum_adaptations,
    curriculum_plan_progress,
    curriculum_plan_entries,
    curriculum_plan_subjects,
    curriculum_plans,
    class_groups,
    course_enrollments,
    courses,
    students,
    academic_trimesters,
    subjects,
    grades,
    levels,
    parallels,
    users
RESTART IDENTITY CASCADE;

COMMIT;

-- =============================================================================
-- VERIFICACION
-- =============================================================================
-- Las tres primeras filas tienen que traer datos. Todas las demas, cero.
SELECT 'roles'            AS tabla, COUNT(*) AS filas FROM roles
UNION ALL SELECT 'knowledge_areas',  COUNT(*) FROM knowledge_areas
UNION ALL SELECT 'academic_years',   COUNT(*) FROM academic_years
UNION ALL SELECT 'users',            COUNT(*) FROM users
UNION ALL SELECT 'levels',           COUNT(*) FROM levels
UNION ALL SELECT 'grades',           COUNT(*) FROM grades
UNION ALL SELECT 'parallels',        COUNT(*) FROM parallels
UNION ALL SELECT 'subjects',         COUNT(*) FROM subjects
UNION ALL SELECT 'academic_trimesters', COUNT(*) FROM academic_trimesters
UNION ALL SELECT 'courses',          COUNT(*) FROM courses
UNION ALL SELECT 'class_groups',     COUNT(*) FROM class_groups
UNION ALL SELECT 'students',         COUNT(*) FROM students
UNION ALL SELECT 'course_enrollments', COUNT(*) FROM course_enrollments
ORDER BY tabla;

-- Los roles que tienen que existir para poder crear usuarios.
SELECT id_role, name FROM roles ORDER BY id_role;
