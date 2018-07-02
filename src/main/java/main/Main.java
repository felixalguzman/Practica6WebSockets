package main;

import encapsulacion.*;
import freemarker.template.Configuration;
import freemarker.template.Version;
import modelo.servicios.EntityServices.*;
import modelo.servicios.Utils.BootStrapService;
import modelo.servicios.Utils.Crypto;
import modelo.servicios.Utils.Filtros;
//import org.jasypt.util.text.StrongTextEncryptor;
import spark.ModelAndView;
import spark.Session;
import spark.template.freemarker.FreeMarkerEngine;

import java.util.*;
import java.sql.SQLException;

import static spark.Spark.*;

public class Main {

    public static Usuario usuario;
    static final String iv = "0123456789123456"; // This has to be 16 characters
    static final String secretKeyUSer = "qwerty987654321";
    static final String secretKeyContra = "123456789klk";

    public static void main(String[] args) throws SQLException {

        BootStrapService.startDb();

        BootStrapService.crearTablas();


        UsuarioService usuarioService = new UsuarioService();
        ArticuloService articuloService = new ArticuloService();
        EtiquetaService etiquetaService = new EtiquetaService();
        ComentarioService comentarioService = new ComentarioService();
        LikesService likesService = new LikesService();


        staticFiles.location("/templates");

        Configuration configuration = new Configuration(new Version(2, 3, 0));
        configuration.setClassForTemplateLoading(Main.class, "/templates");

        FreeMarkerEngine freeMarkerEngine = new FreeMarkerEngine(configuration);


        get("/", (request, response) -> {
            Map<String, Object> attributes = new HashMap<>();
            Map<String, String> cookies = request.cookies();

            String[] llaveValor = new String[2];
            request.cookie("login");
            for (String key : cookies.keySet()) {
                System.out.println("llave: " + key + " valor: " + cookies.get(key));
                llaveValor = cookies.get(key).split(",");

            }


            if (llaveValor.length > 1) {
                Crypto crypto = new Crypto();

                System.out.println(llaveValor[0] + " contra: " + llaveValor[1]);
                String user = crypto.decrypt(llaveValor[0], iv, secretKeyUSer);
                String contra = crypto.decrypt(llaveValor[1], iv, secretKeyContra);

                Usuario usuario1 = usuarioService.validateLogIn(user, contra);
                if (usuario1 != null) {
                    usuario = usuario1;
                    request.session(true);
                    request.session().attribute("usuario", usuario);
                    response.redirect("/inicio/1");
//                    return modelAndView(attributes, "inicio.ftl");
                }
            }
            return new ModelAndView(attributes, "login.ftl");
        }, freeMarkerEngine);

        get("/inicio/:pag", (request, response) -> {

            String p = request.params("pag");
            int pagina = Integer.parseInt(p);

            Map<String, Object> attributes = new HashMap<>();
            attributes.put("titulo", "Inicio");

            attributes.put("list", articuloService.getPagination(pagina));
            attributes.put("actual", pagina);

            attributes.put("paginas", Math.ceil(articuloService.cantPaginas()/5f));


            attributes.put("etiquetas", etiquetaService.getAll());
            attributes.put("usuario", usuario);

            return new ModelAndView(attributes, "inicio.ftl");
        }, freeMarkerEngine);

//        get("/inicio/pag/:n/:operacion", (request, response) -> {
//            Map<String, Object> attributes = new HashMap<>();
//            String pag = request.params("n");
//            String op = request.params("operacion");
//            int pagina = Integer.parseInt(pag);
//            if (op.equalsIgnoreCase("mas")) {
//                pagina++;
//            } else {
//                pagina--;
//            }
//
//
//            attributes.put("titulo", "Inicio");
//
//            attributes.put("list", articuloService.getPagination(pagina));
//
//            attributes.put("pagina", pagina);
//            attributes.put("etiquetas", etiquetaService.getAll());
//            attributes.put("usuario", usuario);
//
//            return new ModelAndView(attributes, "inicio.ftl");
//        }, freeMarkerEngine);


        get("/verMas/:id", (request, response) -> {
            Map<String, Object> attributes = new HashMap<>();
            String idArticulo = request.params("id");

            Articulo articulo2 = articuloService.getById(Integer.parseInt(idArticulo));
            attributes.put("articulo", articulo2);
            attributes.put("comentarios", articulo2.getListaComentarios());
            attributes.put("etiquetas", articulo2.getListaEtiquetas());
            attributes.put("cantLikes", likesService.LikesByArticuloId(articulo2.getId()));
            attributes.put("cantDislikes", likesService.DislikesByArticuloId(articulo2.getId()));
            attributes.put("usuario", usuario);

            return new ModelAndView(attributes, "post.ftl");
        }, freeMarkerEngine);

        get("/articulos", (request, response) -> {
            Map<String, Object> attributes = new HashMap<>();
            String etiqueta = request.queryParams("etiqueta");

            attributes.put("titulo", "Articulos por: " + etiqueta);
            attributes.put("list", articuloService.getAllByEtiqueta(etiqueta));
            attributes.put("etiquetas", etiquetaService.getAll());
            attributes.put("usuario", usuario);


            return new ModelAndView(attributes, "inicio.ftl");
        }, freeMarkerEngine);


        post("/agregarComentario", (request, response) -> {

            if (usuario == null)
                response.redirect("/errorPost");

            String comentario = request.queryParams("comentario");
            String articulo = request.queryParams("articulo");
            String autor = request.queryParams("autor");

            Usuario usuario1 = usuarioService.getById(Integer.parseInt(autor));
            Articulo articulo1 = articuloService.getById(Integer.parseInt(articulo));

            Comentario comentario1 = new Comentario(comentario, usuario1, articulo1);

            comentarioService.insert(comentario1);


            response.redirect("/verMas/" + articulo);
            return "";
        });

        get("/agregarPost", (request, response) -> configuration.getTemplate("agregarPost.ftl"));
        get("/editarPost/:id", (request, response) -> {
            Map<String, Object> attributes = new HashMap<>();
            String idArticulo = request.params("id");
            long articleid = Integer.parseInt(idArticulo);

            List<Articulo> a = new ArrayList<>();

            if (usuario != null) {

                a = articuloService.getbyAutor(usuario.getId());
            } else {
                response.redirect("/errorPost");
            }

            if (articuloService.buscarPost(a, articleid) || usuario.getAdministrator()) {

                Articulo editar = articuloService.getById(Integer.parseInt(idArticulo));
                attributes.put("articulo", editar);
                List<Etiqueta> tags = etiquetaService.getByArticulo(Integer.parseInt(idArticulo));
                StringBuilder res = new StringBuilder();
                for (int i = 0; i < tags.size(); i++) {
                    if (i == tags.size() - 1) {
                        res.append(tags.get(i).getEtiqueta());
                    } else {
                        res.append(tags.get(i).getEtiqueta()).append(",");
                    }
                }
                String ResultingTagString = String.valueOf(res);
                attributes.put("etiquetas", ResultingTagString);
            } else {
                response.redirect("/errorPost");
            }


            return new ModelAndView(attributes, "editarPost.ftl");
        }, freeMarkerEngine);

        post("/guardarPost", (request, response) -> {
            Usuario autor = usuario;
            String titulo = request.queryParams("titulo");
            String cuerpo = request.queryParams("cuerpo");
            long now = System.currentTimeMillis();
            java.sql.Date nowsql = new java.sql.Date(now);

            String etiquetas = request.queryParams("etiquetas");

            String[] tagsarray = etiquetas.split(",");
//            Long articleid = articuloService.getNextID();
            Articulo art = new Articulo(titulo, cuerpo, autor, nowsql);
//            articuloService.insert(art);

            Set<Etiqueta> etiquetas1 = new HashSet<>();
//

            for (String s : tagsarray) {
                Etiqueta e = new Etiqueta(s, art);
                etiquetas1.add(e);
            }

            art.setListaEtiquetas(etiquetas1);
            articuloService.insert(art);


            response.redirect("/inicio/1");
            return "";
        });

        post("/iniciarSesion", (request, response) -> {

            String user = request.queryParams("usuario");
            String contra = request.queryParams("password");
            String recordar = request.queryParams("remember");

            System.out.println(recordar);


            System.out.println(user + " pass : " + contra);
            Usuario usuario1 = usuarioService.validateLogIn(user, contra);

            if (usuario1 != null) {

                if (recordar != null && recordar.equalsIgnoreCase("on")) {


                    Crypto crypto = new Crypto();
                    String userEncrypt = crypto.encrypt(user, iv, secretKeyUSer);
                    String contraEncrypt = crypto.encrypt(contra, iv, secretKeyContra);


//                final String decryptedData = crypto.decrypt(encryptedData, iv, secretKey);

                    System.out.println("user encryp: " + userEncrypt + " contra encryp: " + contraEncrypt);

                    response.cookie("/", "login", userEncrypt + "," + contraEncrypt, 604800, false); //incluyendo el path del cookie.
                }

                usuario = usuario1;
                request.session(true);
                request.session().attribute("usuario", usuario);
                response.redirect("/inicio/1");
            }
            return "";
        });

        post("/editarPost/:id", (request, response) -> {


            String idArticulo = request.params("id");
            Long articleid = Long.parseLong(idArticulo);


            Usuario autor = usuario;

            String titulo = request.queryParams("titulo");
            String cuerpo = request.queryParams("cuerpo");
            long now = System.currentTimeMillis();
            java.sql.Date nowsql = new java.sql.Date(now);
            Articulo art = new Articulo(articleid, titulo, cuerpo, autor, nowsql);
            articuloService.update(art);
            String tags = request.queryParams("etiquetas");

            String[] tagArray = tags.split(",");

            List<Etiqueta> l = etiquetaService.getByArticulo(articleid);

            for (String aTagArray : tagArray) {
                boolean exists = false;
                for (Etiqueta e : l) {
                    if (aTagArray.equalsIgnoreCase(e.getEtiqueta())) {
                        exists = true;
                    }
                }
                if (!exists) {
                    etiquetaService.insert(new Etiqueta(aTagArray, art));
                }
            }
            response.redirect("/verMas/" + idArticulo);


            return "";
        });

        get("/eliminarPost/:id/:articulo", (request, response) -> {

            String id = request.params("id");
            String articulo = request.params("articulo");

            long idAutor = Integer.parseInt(id);
            long idArticulo = Integer.parseInt(articulo);

            if (usuario != null && (idAutor == usuario.getId() || usuario.getAdministrator())) {
                articuloService.delete(articuloService.getById(idArticulo));
                response.redirect("/inicio/1");
            } else {
                response.redirect("/errorPost");
            }

            return "";
        });

        get("/agregarUsuario", (request, response) -> configuration.getTemplate("agregarUsuario.ftl"));

        post("/guardarUsuario", (request, response) -> {
            String username = request.queryParams("usuario");
            String nombre = request.queryParams("nombre");
            String pass = request.queryParams("pass");
            String autor = request.queryParams("autor");
            String admin = request.queryParams("admin");
            Usuario u = new Usuario(username, nombre, pass, admin != null, autor != null);
            usuarioService.insert(u);
            response.redirect("/inicio/1");
            return "";
        });


        get("/logOut", (request, response) -> {

            usuario = null;
            Session session = request.session(true);
            session.invalidate();
            response.removeCookie("/", "login");
            response.redirect("/inicio/1");

            return "";
        });

        get("/errorPost", (request, response) -> {
            Map<String, Object> attributes = new HashMap<>();

            attributes.put("mensaje", "Usted no tiene permisos para esta area!");

            return new ModelAndView(attributes, "error.ftl");
        }, freeMarkerEngine);

        get("/verUsuarios", (request, response) -> {
            Map<String, Object> attributes = new HashMap<>();

            attributes.put("usuarios", usuarioService.getAll());

            return new ModelAndView(attributes, "verUsuarios.ftl");
        }, freeMarkerEngine);


        get("/ver/:id", (request, response) -> {
            String id = request.params("id");

            Usuario usuario = usuarioService.getById(Integer.parseInt(id));


            Map<String, Object> attributes = new HashMap<>();
            attributes.put("titulo", "Posts de " + usuario.getNombre());
            attributes.put("usuario", usuario);
            attributes.put("etiquetas", etiquetaService.getAll());
            attributes.put("list", articuloService.getbyAutor(usuario.getId()));

            return new ModelAndView(attributes, "inicio.ftl");
        }, freeMarkerEngine);


        get("/like/:post", (request, response) -> {

            String post = request.params("post");

            int idPost = Integer.parseInt(post);

            Usuario usuario = request.session(true).attribute("usuario");
            Articulo articulo = articuloService.getById(idPost);
            if (usuario != null) {

                if (!likesService.existsUsuario(usuario.getId(), TipoLike.LIKE) && !likesService.existsUsuario(usuario.getId(), TipoLike.DISLIKE)) {




                    Likes likes1 = new Likes(articulo, usuario, TipoLike.LIKE);

                    likesService.guardar(likes1);


                    response.redirect("/verMas/" + articulo.getId());

                }else {

                    if (likesService.existsUsuario(usuario.getId(), TipoLike.LIKE)){

                        Likes likes = likesService.buscarByUsuario(usuario);
                        likesService.borrar(likes);
                    }

                    if (likesService.existsUsuario(usuario.getId(), TipoLike.DISLIKE)){

                        Likes likes = likesService.buscarByUsuario(usuario);
                        likesService.borrar(likes);

                        Likes likes1 = new Likes(articulo, usuario, TipoLike.LIKE);
                        likesService.guardar(likes1);


                    }


                    response.redirect("/verMas/" + idPost);
                }

            } else {
                response.redirect("/errorPost");
            }
            return "";
        });

        get("dislike/:post", (request, response) -> {

            String post = request.params("post");

            int idPost = Integer.parseInt(post);
            Usuario usuario = request.session(true).attribute("usuario");
            Articulo articulo = articuloService.getById(idPost);

            if (usuario != null) {

                if (!likesService.existsUsuario(usuario.getId(), TipoLike.DISLIKE) && !likesService.existsUsuario(usuario.getId(), TipoLike.LIKE)) {


                    Likes likes1 = new Likes(articulo, usuario, TipoLike.DISLIKE);
                    likesService.guardar(likes1);


                    response.redirect("/verMas/" + articulo.getId());

                }else {

                    if (likesService.existsUsuario(usuario.getId(), TipoLike.DISLIKE)){
                        Likes likes = likesService.buscarByUsuario(usuario);
                        likesService.borrar(likes);

                    }

                    if (likesService.existsUsuario(usuario.getId(), TipoLike.LIKE)){

                        Likes likes = likesService.buscarByUsuario(usuario);
                        likesService.borrar(likes);

                        Likes likes1 = new Likes(articulo, usuario, TipoLike.DISLIKE);
                        likesService.guardar(likes1);


                    }




                    response.redirect("/verMas/" + idPost);
                }

            } else {
                response.redirect("/errorPost");
            }

            return "";
        });


        new Filtros().filtros();
    }
}

