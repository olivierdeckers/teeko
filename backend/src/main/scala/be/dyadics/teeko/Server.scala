package be.dyadics.teeko

import cats.effect.{Blocker, ExitCode}
import org.http4s.HttpApp
import org.http4s.server.blaze.BlazeServerBuilder
import pureconfig.{ConfigReader, ConfigSource, Exported}
import zio.blocking.Blocking
import zio.console.putStrLn
import zio.{App, RIO, Runtime, Task, ZEnv, ZIO}

final case class Config(env: String)

object Main extends App {
  import cats.implicits._
  import org.http4s.implicits._
  import zio.interop.catz._

  type AppEnvironment = ZEnv
  type AppTask[A] = RIO[AppEnvironment, A]

  /*
   * Ok. I'm not proud of this solution but it's simple.
   *
   * If you have a better solution to propose, I'm ready to listen you :)
   */
  private def env(cfg: Config): Env =
    cfg.env match {
      case "production" | "staging" => Env.Prod(cfg.env)
      case "dev" => Env.Dev
      case "test" => Env.Test
    }

  /*
   * Here, I need the `.absorbWith` because the pureconfig error channel contains a case class which doesn't herit from a Throwable.
   *
   * I don't know how to fix the compilation without this hack. 😕
   */
  private val config: ZIO[Any, Throwable, Config] = {
    import pureconfig.generic.auto._
    implicitly[Exported[
      ConfigReader[Config]
    ]] // ⚠️ Without, Intellij removes `pureconfig.generic.auto._` import...

    ZIO
      .fromEither(ConfigSource.default.load[Config])
      .absorbWith(error => new RuntimeException(error.toString))
  }

  private val blocker: ZIO[Blocking, Nothing, Blocker] =
    ZIO
      .access[Blocking](_.get.blockingExecutor.asEC)
      .map(Blocker.liftExecutionContext)

//  private def logged[F[_]: Concurrent](httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
//    Logger.httpRoutes(logHeaders = true, logBody = true)(httpRoutes)

  private def app(
      env: Env
    )(
      implicit blocker: Blocker,
      runtime: Runtime[AppEnvironment]
    ): AppTask[HttpApp[AppTask]] =
    new WebsocketRouter[AppEnvironment]().routes.map(websocketRoutes => {
      (new FrontendRouter[AppEnvironment](env).routes <+> websocketRoutes).orNotFound
    })

  private def server: ZIO[AppEnvironment, Throwable, Unit] =
    for {
      cfg <- config
      env <- env(cfg).pure[Task]
      _ <- console.putStrLn(s"========= App ENV: $env ===========")
      implicit0(runtime: Runtime[AppEnvironment]) <- ZIO.runtime[ZEnv]
      implicit0(blocker: Blocker) <- blocker
      routes <- app(env)
      server <- BlazeServerBuilder[AppTask]
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(routes)
        .serve
        .compile[AppTask, AppTask, ExitCode]
        .drain
    } yield server

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    server.foldM(
      err => putStrLn(s"Execution failed with: $err") *> ZIO.succeed(1),
      _ => ZIO.succeed(0)
    )

}
