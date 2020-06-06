package be.dyadics.teeko

import java.util.UUID

import cats.effect.Blocker
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`
import org.http4s.{Charset, Headers, HttpRoutes, MediaType}
import zio.{RIO, Runtime}

object FrontendRouter {

  private final val assetsDir = "assets"
  private final val indexHeaders: Headers =
    Headers.of(`Content-Type`(MediaType.text.html, Charset.`UTF-8`))

  private final def scripts(env: Env): String =
    env match {
      case Env.Prod(_) =>
        s"""<script type="text/javascript" src="$assetsDir/frontend-opt-bundle.js"></script>"""
      case _ =>
        s"""
           |<script type="text/javascript" src="$assetsDir/frontend-fastopt-library.js"></script>
           |<script type="text/javascript" src="$assetsDir/frontend-fastopt-loader.js"></script>
           |<script type="text/javascript" src="$assetsDir/frontend-fastopt.js"></script>
           |""".stripMargin
    }

  private final def html(env: Env): String =
    s"""
       |<!DOCTYPE html>
       |<html>
       |<head>
       |  <meta charset="UTF-8">
       |  <title>Teeko</title>
       |  <link rel="shortcut icon" type="image/png" href="$assetsDir/images/favicon.png"/>
       |  <meta name="viewport" content="width=device-width, initial-scale=1.0">
       |</head>
       |<body>
       |  <div id="app"></div>
       |
       |  ${scripts(env)}
       |</body>
       |</html>
       |""".stripMargin
}

final class FrontendRouter[R](env: Env)(implicit blocker: Blocker, runtime: Runtime[R]) {
  import FrontendRouter._
  import org.http4s.server.staticcontent._
  import zio.interop.catz._

  type Task[A] = RIO[R, A]

  private val dsl: Http4sDsl[Task] = Http4sDsl[Task]
  import dsl._

  private val resources: HttpRoutes[Task] =
    resourceService[Task](
      ResourceService.Config("/public", blocker, pathPrefix = s"/$assetsDir")
    )

  private val notFound = NotFound()

  val routes: HttpRoutes[Task] =
    HttpRoutes.of[Task] {
      case req @ GET -> FrontendRouter.assetsDir /: _ =>
        resources.run(req).getOrElseF(notFound)
      case req @ GET -> Root =>
        val requestId =
          req.cookies.find(_.name == "playerId").fold(UUID.randomUUID().toString)(_.content)
        Ok(html(env))
          .map(_.withHeaders(indexHeaders))
          .map(_.addCookie("playerId", requestId))
    }

}
