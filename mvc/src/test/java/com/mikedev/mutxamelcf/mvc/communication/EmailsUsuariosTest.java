package com.mikedev.mutxamelcf.mvc.communication;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

/** Los textos de los emails a usuarios de la app (emails_es|ca|en.properties) y lo que arma ComunicacionesService. */
class EmailsUsuariosTest {

    private static final Pattern MARCADOR = Pattern.compile("\\{([A-Za-z0-9_]+)}");

    private static Properties cargar(String idioma) throws Exception {

        Properties p = new Properties();

        try (InputStream in = new ClassPathResource("emails/emails_" + idioma + ".properties").getInputStream()) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        return p;
    }

    private static Set<String> marcadores(String texto) {

        Set<String> set = new TreeSet<>();
        Matcher m = MARCADOR.matcher(texto);

        while (m.find()) {
            set.add(m.group(1));
        }

        return set;
    }

    // ---- los tres ficheros ---------------------------------------------------------------------------------------------

    @Test
    void lasTresVersionesTienenLasMismasClavesYLosMismosMarcadores() throws Exception {

        Properties es = cargar("es");

        for (String idioma : List.of("ca", "en")) {

            Properties otro = cargar(idioma);

            assertThat(new TreeSet<>(otro.stringPropertyNames())).as("claves de " + idioma)
                    .isEqualTo(new TreeSet<>(es.stringPropertyNames()));

            for (String clave : es.stringPropertyNames()) {
                assertThat(marcadores(otro.getProperty(clave))).as(clave + " en " + idioma)
                        .isEqualTo(marcadores(es.getProperty(clave)));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "es", "ca", "en" })
    void ningunaClaveEstaVaciaNiMencionaLas48HorasNiMutxamelCFSinPuntos(String idioma) throws Exception {

        Properties p = cargar(idioma);

        for (String clave : p.stringPropertyNames()) {

            String texto = p.getProperty(clave);

            assertThat(texto).as(clave).isNotBlank();
            assertThat(texto).as(clave).doesNotContainPattern("48\\s*(horas|hores|hours|h\\b)");
            assertThat(texto).as(clave).doesNotContain("Mutxamel CF");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "es", "ca", "en" })
    void coordinacionNoDiceMisEquiposYLaInvitacionUsaElNombreDeCadaIdioma(String idioma) throws Exception {

        Properties p = cargar(idioma);
        String coordinador = p.getProperty("rol.COORDINADOR");

        assertThat(coordinador).doesNotContain("Mis equipos").doesNotContain("Els meus equips")
                .doesNotContain("My teams");

        String invitacion = p.getProperty("invitacion.cuerpo");

        switch (idioma) {
            case "es" -> assertThat(invitacion).contains("Mutxamel Club de Fútbol").doesNotContain("Club de Futbol");
            case "ca" -> assertThat(invitacion).contains("Mutxamel Club de Futbol");
            default -> assertThat(invitacion).contains("official Mutxamel C.F. app").doesNotContain("Club de Futbol");
        }
    }

    // ---- cuenta activada, con cada rol -----------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "es,Seguir en directo los partidos del Primer Equipo y añadir los partidos a tu calendario.",
            "ca,Seguir en directe els partits del Primer Equip i afegir els partits al teu calendari.",
            "en,Follow the First Team matches live and add matches to your calendar." })
    void laListaGeneralDeLaCuentaActivadaIncluyeElDirectoYElCalendario(String idioma, String linea) {

        String texto = ComunicacionesService.construirCuentaActivada("Ana", List.of("JUGADOR"), idioma);

        assertThat(texto).contains("• " + linea);

        // Va después de la línea de próximos partidos y resultados (y de las dos nuevas: Inicio y Mutxigol).
        int resultados = texto.indexOf(idioma.equals("es") ? "Consultar los próximos partidos y resultados."
                : idioma.equals("ca") ? "Consultar els pròxims partits i resultats."
                        : "Check upcoming matches and results.");

        assertThat(texto.indexOf(linea)).isGreaterThan(resultados);
        assertThat(texto.indexOf(linea) - resultados).isLessThan(400);
        assertThat(texto).doesNotContain("{");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "JUGADOR|es|Como jugador|Puedes escribir a tu entrenador",
            "FAMILIAR|es|Como familiar|Pedir acceso a otro jugador",
            "ENTRENADOR|es|Como entrenador|Cambias el dorsal y la posición de tus jugadores.",
            "ENTRENADOR|es|Como entrenador|Revisas las solicitudes de acceso de las familias de tus jugadores.",
            "COORDINADOR|es|Como coordinador|«Coordinación › Todos los equipos»",
            "COORDINADOR|es|Como coordinador|En «Fin de semana» ves el estado de la jornada",
            "COORDINADOR|es|Como coordinador|Revisas las solicitudes de acceso de las familias.",
            "RETRANSMISION|es|Como responsable de retransmisión|publicas la alineación\\, los goles\\, los cambios y las tarjetas",
            "RETRANSMISION|es|Como responsable de retransmisión|Este rol es solo para el directo",
            "RRSS|es|Para las redes sociales del club|«Directo y RRSS › Imágenes para redes» generas la previa y los resultados del fin de semana y los carteles del próximo partido del Primer Equipo y del Filial, listos para publicar.",
            "ADMIN_APP|es|Como administrador de la app|En «Moderación» revisas los mensajes reportados",
            "FAMILIAR|ca|Com a familiar|Demanar accés a un altre jugador",
            "ENTRENADOR|ca|Com a entrenador|Canvies el dorsal i la posició dels teus jugadors.",
            "COORDINADOR|ca|Com a coordinador|«Coordinació › Tots els equips»",
            "RETRANSMISION|ca|Com a responsable de retransmissió|publiques l'alineació",
            "RRSS|ca|Per a les xarxes socials del club|«Directe i XXSS › Imatges per a xarxes» generes la prèvia",
            "RRSS|ca|Per a les xarxes socials del club|el divendres a les 12:00 la prèvia no està feta",
            "ADMIN_APP|ca|Com a administrador de l'app|A «Moderació» revises els missatges reportats",
            "ENTRENADOR|ca|Com a entrenador|A «Pendents», damunt de l'Àrea Club",
            "COORDINADOR|ca|Com a coordinador|A «Cap de setmana» veus l'estat de la jornada: partits sense convocatòria",
            "COORDINADOR|ca|Com a coordinador|A «Seguiment de jugadors» veus qui acumula faltes",
            "RETRANSMISION|ca|Com a responsable de retransmissió|«Directe i XXSS › Partit en directe»",
            "ENTRENADOR|es|Como entrenador|En «Pendientes», arriba del Área Club, ves lo que te falta: listas sin pasar, convocatorias sin publicar y resultados sin poner.",
            "COORDINADOR|es|Como coordinador|partidos sin convocatoria, resultados pendientes y equipos que descansan, y puedes recordárselo a cada entrenador.",
            "COORDINADOR|es|Como coordinador|En «Seguimiento de jugadores» ves quién acumula faltas o mal comportamiento, y en «Pendientes» lo que queda por hacer en todo el club.",
            "RETRANSMISION|es|Como responsable de retransmisión|En «Directo y RRSS › Partido en directo» publicas",
            "RETRANSMISION|es|Como responsable de retransmisión|todo se ve en Inicio y en la ficha del partido",
            "RRSS|es|Para las redes sociales del club|Si el viernes a las 12:00 la previa no está hecha, o el lunes a las 9:00 los resultados, te llega un recordatorio; también lo verás en «Pendientes».",
            "ENTRENADOR|en|As a coach|In \"To do\", at the top of the Club Area",
            "COORDINADOR|en|As a coordinator|In \"Weekend\" you see the status of the matchday: matches without a call-up",
            "COORDINADOR|en|As a coordinator|In \"Player follow-up\" you see who is piling up absences",
            "RETRANSMISION|en|As the live broadcast person|\"Live & social media › Live match\"",
            "RRSS|en|For the club's social media|\"Live & social media › Social media images\"",
            "RRSS|en|For the club's social media|If the preview is not done by Friday at 12:00",
            "ADMIN_APP|ca|Com a administrador de l'app|A «Administració › Usuaris» busques i filtres",
            "ADMIN_APP|ca|Com a administrador de l'app|A «Administració › Pujar dades FFCV»",
            "ADMIN_APP|es|Como administrador de la app|En «Administración › Subir datos FFCV» importas desde el móvil",
            "ADMIN_APP|en|As the app administrator|In \"Administration › Upload FFCV data\"",
            "FAMILIAR|en|As a family member|Ask for access to another player",
            "ENTRENADOR|en|As a coach|You change the shirt number and position of your players.",
            "COORDINADOR|en|As a coordinator|Coordination › All teams",
            "RETRANSMISION|en|As the live broadcast person|lineup",
            "RRSS|en|For the club's social media|Social media images\" you generate the weekend preview and results",
            "ADMIN_APP|en|As the app administrator|reported messages and the Mutxigol game aliases" })
    void cadaRolAnadeSuBloqueALaCuentaActivada(String rol, String idioma, String titulo, String frase) {

        String texto = ComunicacionesService.construirCuentaActivada("Ana", List.of(rol), idioma);

        assertThat(texto).contains(titulo).contains(frase.replace("\\,", ","));
        assertThat(texto).doesNotContain("{roles}").doesNotContain("{saludo}");
    }

    @Test
    void unaCuentaConAdminAppRecibeSuBloqueYElDeCoordinacionPeroNoElDeOtrosRoles() {

        String es = ComunicacionesService.construirCuentaActivada("Ana", List.of("ADMIN_APP"), "es");

        assertThat(es).contains("Como administrador de la app")
                .contains("• Tienes acceso completo a la app, incluidos el Partido en directo y las Imágenes para redes.")
                .contains("• En «Moderación» revisas los mensajes reportados y los alias del juego de Mutxigol")
                // Sigue viendo lo mismo que un coordinador.
                .contains("Como coordinador")
                .doesNotContain("Como entrenador").doesNotContain("Como jugador").doesNotContain("Como familiar")
                // Retransmisión y RRSS los resume su propio bloque: no se repiten.
                .doesNotContain("Como responsable de retransmisión")
                .doesNotContain("Para las redes sociales del club");

        for (String idioma : List.of("ca", "en")) {
            String texto = ComunicacionesService.construirCuentaActivada("Ana", List.of("ADMIN_APP"), idioma);

            assertThat(texto).contains(idioma.equals("ca") ? "Com a administrador de l'app" : "As the app administrator")
                    .contains(idioma.equals("ca") ? "Com a coordinador" : "As a coordinator")
                    .doesNotContain(idioma.equals("ca") ? "Com a responsable de retransmissió" : "As the live broadcast person")
                    .doesNotContain(idioma.equals("ca") ? "Per a les xarxes socials del club"
                            : "For the club's social media");
        }
    }

    private static String titulo(String idioma, String rol) {
        return switch (idioma) {
            case "ca" -> rol.equals("COORDINADOR") ? "Com a coordinador" : "Com a responsable de retransmissió";
            case "en" -> rol.equals("COORDINADOR") ? "As a coordinator" : "As the live broadcast person";
            default -> rol.equals("COORDINADOR") ? "Como coordinador" : "Como responsable de retransmisión";
        };
    }

    @Test
    void unAdminConRetransmisionYRrssSoloLlevaSusBloquesYElDeCoordinacion() {

        for (String idioma : List.of("es", "ca", "en")) {
            String texto = ComunicacionesService.construirCuentaActivada(
                    "Ana", List.of("ADMIN_APP", "RETRANSMISION", "RRSS"), idioma);

            assertThat(texto).as(idioma)
                    .contains(titulo(idioma, "COORDINADOR"))
                    .contains(idioma.equals("es") ? "Como administrador de la app"
                            : idioma.equals("ca") ? "Com a administrador de l'app" : "As the app administrator")
                    .doesNotContain(titulo(idioma, "RETRANSMISION"))
                    .doesNotContain(idioma.equals("es") ? "Para las redes sociales del club"
                            : idioma.equals("ca") ? "Per a les xarxes socials del club" : "For the club's social media");
        }
    }

    @Test
    void unaCuentaSoloRrssLlevaSuBloqueYNadaMas() {

        for (String idioma : List.of("es", "ca", "en")) {
            String texto = ComunicacionesService.construirCuentaActivada("Ana", List.of("RRSS"), idioma);

            assertThat(texto).as(idioma)
                    .contains(idioma.equals("es") ? "Para las redes sociales del club"
                            : idioma.equals("ca") ? "Per a les xarxes socials del club" : "For the club's social media")
                    .doesNotContain(titulo(idioma, "COORDINADOR"))
                    .doesNotContain(titulo(idioma, "RETRANSMISION"));
        }
    }

    @Test
    void elValencianoEscribePrevisaConAcentoEnTodosSusTextos() throws Exception {

        for (String clave : cargar("ca").stringPropertyNames()) {
            assertThat(cargar("ca").getProperty(clave)).as(clave).doesNotContainPattern("(?i)\\bprevia\\b");
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "es|En Inicio, el próximo partido de tus equipos o de tus hijos con su convocatoria: hora y lugar de citación y equipaje.|Jugar con Mutxigol, la mascota del club, y entrar en el ranking con un alias (nunca sale tu nombre).",
            "ca|A l'Inici, el pròxim partit dels teus equips o dels teus fills amb la seua convocatòria: hora i lloc de citació i equipació.|Jugar amb Mutxigol, la mascota del club, i entrar al rànquing amb un àlies (mai no ix el teu nom).",
            "en|On Home, the next match of your teams or your children's teams with its call-up: meeting time and place, and kit.|Play Mutxigol, the club mascot, and join the ranking with an alias (your name never appears)." })
    void laCuentaActivadaMencionaElProximoPartidoYMutxigolDespuesDeLaSegundaLinea(
            String idioma, String proximo, String mutxigol) {

        String texto = ComunicacionesService.construirCuentaActivada("Ana", List.of("JUGADOR"), idioma);
        String resultados = idioma.equals("es") ? "Consultar los próximos partidos y resultados."
                : idioma.equals("ca") ? "Consultar els pròxims partits i resultats."
                        : "Check upcoming matches and results.";
        String directo = idioma.equals("es") ? "Seguir en directo"
                : idioma.equals("ca") ? "Seguir en directe" : "Follow the First Team";

        // Las líneas actuales se quedan; las nuevas van justo después de la segunda y antes de la tercera.
        assertThat(texto).contains("• " + proximo + "\n• " + mutxigol + "\n");
        assertThat(texto.indexOf(proximo)).isGreaterThan(texto.indexOf(resultados));
        assertThat(texto.indexOf(mutxigol)).isLessThan(texto.indexOf(directo));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "es|pide al club que te reenvíe la invitación (escribe a mutxamelcf.gestion@gmail.com)",
            "ca|demana al club que et reenvie la invitació (escriu a mutxamelcf.gestion@gmail.com)",
            "en|ask the club to resend the invitation (write to mutxamelcf.gestion@gmail.com)" })
    void laInvitacionYaNoMandaALaOficinaSinoAlClubConSuEmail(String idioma, String frase) throws Exception {

        String cuerpo = cargar(idioma).getProperty("invitacion.cuerpo").replace(" \n", " ");

        assertThat(cuerpo.replaceAll("\\s+", " ")).contains(frase);
        assertThat(cuerpo.toLowerCase()).doesNotContain("oficina").doesNotContain("office");
    }

    @ParameterizedTest
    @ValueSource(strings = { "es", "ca", "en" })
    void ningunaClaveNombraPantallasQueYaNoExisten(String idioma) throws Exception {

        // «Cuentas pendientes» ahora es Usuarios; «Retransmisión» no es un menú; «Fin de semana» ya no lleva las imágenes
        // para redes; y las etiquetas «Directo y RRSS» de la sección se llaman «Directe i XXSS» y «Live & social media».
        for (String valor : cargar(idioma).stringPropertyNames().stream()
                .map(cargar(idioma)::getProperty).toList()) {
            assertThat(valor).doesNotContain("Cuentas pendientes").doesNotContain("Comptes pendents")
                    .doesNotContain("Pending accounts").doesNotContain("«Retransmisión»")
                    .doesNotContain("«Retransmissió»").doesNotContain("Directe i RRSS")
                    .doesNotContain("Live and social media");
        }

        assertThat(cargar(idioma).getProperty("rol.COORDINADOR"))
                .doesNotContain("imágenes").doesNotContain("imatges").doesNotContain("images");
    }

    @Test
    void unRolSinAdminAppNoLlevaElBloqueDeAdministrador() {

        for (String rol : List.of("JUGADOR", "FAMILIAR", "ENTRENADOR", "COORDINADOR", "RETRANSMISION", "RRSS")) {
            assertThat(ComunicacionesService.construirCuentaActivada("Ana", List.of(rol), "es"))
                    .as(rol).doesNotContain("administrador de la app");
        }
    }

    @Test
    void elBloqueDelAdministradorTieneElFormatoPedido() throws Exception {

        assertThat(cargar("es").getProperty("rol.ADMIN_APP")).isEqualTo("\nComo administrador de la app\n"
                + "• Tienes acceso completo a la app, incluidos el Partido en directo y las Imágenes para redes.\n"
                + "• En «Administración › Usuarios» buscas y filtras todas las cuentas, invitas a personas nuevas, "
                + "cambias sus vínculos y roles, reenvías invitaciones y activas o desactivas cuentas.\n"
                + "• En «Administración › Subir datos FFCV» importas desde el móvil los partidos y las clasificaciones "
                + "de la Federación y la clasificación de veteranos (LVCA).\n"
                + "• En «Moderación» revisas los mensajes reportados y los alias del juego de Mutxigol, "
                + "y en «Estado de la app» ves cómo se está usando.\n");
    }

    // ---- registro de familias: aprobación, rechazo, caducidad ------------------------------------------------------------

    @Test
    void laAprobacionDelPrimerHijoDiceYaTienesAccesoConLosJugadoresYElBloqueDeFamilia() {

        String texto = ComunicacionesService.construirSolicitudFamiliaAprobada("Ana", List.of("Pepe García"), "es");

        assertThat(texto).startsWith("Hola Ana:")
                .contains("El club ha aprobado tu solicitud. Ya puedes ver en la app la información de Pepe García: "
                        + "entra en Área Club con tu email y tu contraseña.")
                .contains("Como familiar").contains("• En «Mis jugadores» ves a los jugadores vinculados a tu cuenta")
                .contains("Si necesitas ayuda, escríbenos a mutxamelcf.gestion@gmail.com.")
                .endsWith("¡Nos vemos en el campo!\nMutxamel C.F.").doesNotContain("{");
    }

    @Test
    void losNombresSeUnenConLaPalabraDeCadaIdioma() {

        assertThat(ComunicacionesService.unirNombres(List.of("Pepe García"), "es")).isEqualTo("Pepe García");
        assertThat(ComunicacionesService.unirNombres(List.of("Pepe García", "Ana García"), "es"))
                .isEqualTo("Pepe García y Ana García");
        assertThat(ComunicacionesService.unirNombres(List.of("Pepe García", "Ana García"), "ca"))
                .isEqualTo("Pepe García i Ana García");
        assertThat(ComunicacionesService.unirNombres(List.of("A", "B", "C"), "en")).isEqualTo("A, B and C");
        assertThat(ComunicacionesService.unirNombres(List.of(), "es")).isEmpty();
        assertThat(ComunicacionesService.construirSolicitudFamiliaAprobada(null,
                List.of("Pepe García", "Ana García"), "es")).startsWith("Hola:")
                .contains("la información de Pepe García y Ana García:");
    }

    @Test
    void laAprobacionTambienSaleEnCatalanEIngles() {

        assertThat(ComunicacionesService.construirSolicitudFamiliaAprobada("Ana", List.of("Pepe"), "ca"))
                .contains("Ja pots veure a l'app la informació de Pepe").contains("Com a familiar")
                .endsWith("Ens veiem al camp!\nMutxamel C.F.");
        assertThat(ComunicacionesService.construirSolicitudFamiliaAprobada("Ana", List.of("Pepe"), "en"))
                .contains("You can now see the information of Pepe in the app").contains("As a family member")
                .endsWith("See you on the pitch!\nMutxamel C.F.");
    }

    @ParameterizedTest
    @CsvSource({ "es,Acceso aprobado a Pepe García,Ya tienes acceso a la app del Mutxamel C.F.",
            "ca,Accés aprovat a Pepe García,Ja tens accés a l'app del Mutxamel C.F.",
            "en,Access approved for Pepe García,You now have access to the Mutxamel C.F. app" })
    void losAsuntosDeLaAprobacionYElEmailCortoDelSiguienteHijo(String idioma, String asuntoOtro, String asuntoPrimero)
            throws Exception {

        Properties p = cargar(idioma);

        assertThat(PlantillasEmail.texto(idioma, "solicitudFamiliaOtroAprobado.asunto", "jugador", "Pepe García"))
                .isEqualTo(asuntoOtro);
        assertThat(p.getProperty("solicitudFamiliaAprobada.asunto")).isEqualTo(asuntoPrimero);

        String corto = PlantillasEmail.texto(idioma, "solicitudFamiliaOtroAprobado.cuerpo", "saludo", "Hola:",
                "jugador", "Pepe García");

        assertThat(corto).contains("Pepe García").endsWith("Mutxamel C.F.").doesNotContain("{");
        // Corto: no repite el bloque de lo que puede hacer una familia.
        assertThat(corto).doesNotContain("mutxamelcf.gestion@gmail.com");
    }

    @Test
    void elEmailCortoDelSiguienteHijoEnCastellanoEsElPedido() throws Exception {

        assertThat(PlantillasEmail.texto("es", "solicitudFamiliaOtroAprobado.cuerpo", "saludo", "Hola Ana:",
                "jugador", "Ana García")).isEqualTo(
                "Hola Ana:\n\nEl club ha aprobado también el acceso a la información de Ana García.\n\nMutxamel C.F.");
    }

    @Test
    void elRechazoLlevaElMotivoSoloSiElRevisorEscribioUno() {

        String con = ComunicacionesService.construirSolicitudFamiliaRechazada("Ana", "Luis Pérez",
                "  No es de nuestro club ", "es");
        String sin = ComunicacionesService.construirSolicitudFamiliaRechazada("Ana", "Luis Pérez", null, "es");
        String vacio = ComunicacionesService.construirSolicitudFamiliaRechazada("Ana", "Luis Pérez", "   ", "es");

        assertThat(con).isEqualTo("Hola Ana:\n\nEl club no ha podido aprobar el acceso a la información de Luis Pérez."
                + "\n\nMotivo: No es de nuestro club\n\nSi crees que es un error, escríbenos a "
                + "mutxamelcf.gestion@gmail.com.\n\nMutxamel C.F.");
        assertThat(sin).isEqualTo("Hola Ana:\n\nEl club no ha podido aprobar el acceso a la información de Luis Pérez."
                + "\n\nSi crees que es un error, escríbenos a mutxamelcf.gestion@gmail.com.\n\nMutxamel C.F.");
        assertThat(vacio).isEqualTo(sin);
        assertThat(sin).doesNotContain("Motivo");
    }

    @Test
    void elRechazoEnCatalanEInglesTraduceElMotivo() throws Exception {

        assertThat(ComunicacionesService.construirSolicitudFamiliaRechazada("Ana", "Luis", "No existe", "ca"))
                .contains("El club no ha pogut aprovar l'accés a la informació de Luis.")
                .contains("\n\nMotiu: No existe\n\n");
        assertThat(ComunicacionesService.construirSolicitudFamiliaRechazada("Ana", "Luis", "Not found", "en"))
                .contains("The club could not approve access to the information of Luis.")
                .contains("\n\nReason: Not found\n\n");
        assertThat(cargar("es").getProperty("solicitudFamiliaRechazada.asunto"))
                .isEqualTo("Sobre tu solicitud de acceso en la app del Mutxamel C.F.");
    }

    @Test
    void laCaducidadTieneDosVariantesSegunSiLaCuentaSeHaBorrado() {

        String borrada = ComunicacionesService.construirSolicitudFamiliaCaducada("Ana", "es", true);
        String conservada = ComunicacionesService.construirSolicitudFamiliaCaducada("Ana", "es", false);

        assertThat(borrada).contains("Tu solicitud de acceso a la app del Mutxamel C.F. ha caducado porque no se ha "
                + "completado en 7 días y hemos borrado los datos de la solicitud.")
                .contains("Si todavía quieres el acceso, puedes volver a pedirlo desde la app, en «Soy familiar de un jugador».")
                .doesNotContain("Pedir acceso a otro jugador");
        assertThat(conservada).contains("ha caducado porque no se ha completado en 7 días y hemos borrado los datos "
                + "de la solicitud.")
                .contains("Si todavía quieres el acceso, puedes volver a pedirlo desde la app, en «Pedir acceso a otro jugador».")
                .doesNotContain("Soy familiar de un jugador");

        for (String idioma : List.of("ca", "en")) {
            assertThat(ComunicacionesService.construirSolicitudFamiliaCaducada("Ana", idioma, true))
                    .contains(idioma.equals("ca") ? "Sóc familiar d'un jugador" : "I'm a relative of a player");
            assertThat(ComunicacionesService.construirSolicitudFamiliaCaducada("Ana", idioma, false))
                    .contains(idioma.equals("ca") ? "Demanar accés a un altre jugador"
                            : "Ask for access to another player");
        }
    }

    // ---- cuenta sin jugadores --------------------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "es|Si tienes otro hijo o hija en el club, puedes pedir acceso desde la app («Pedir acceso a otro jugador») y la cuenta seguirá activa.",
            "ca|Si tens un altre fill o filla al club, pots demanar accés des de l'app («Demanar accés a un altre jugador») i el compte continuarà actiu.",
            "en|If you have another child at the club, you can ask for access from the app (\"Ask for access to another player\") and the account will stay active." })
    void laCuentaSinJugadoresRecuerdaQuePuedePedirAccesoAntesDeLaDespedida(String idioma, String esperada) {

        String texto = ComunicacionesService.construirAvisoCuentaSinJugadores("Ana", LocalDate.of(2026, 12, 1), idioma);

        assertThat(texto).contains(esperada).endsWith("Mutxamel C.F.");
        assertThat(texto.indexOf(esperada)).isLessThan(texto.lastIndexOf("Mutxamel C.F."));
        assertThat(texto).doesNotContain("{");
    }
}
