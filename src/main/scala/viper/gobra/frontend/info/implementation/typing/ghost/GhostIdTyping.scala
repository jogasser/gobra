package viper.gobra.frontend.info.implementation.typing.ghost

import org.bitbucket.inkytonik.kiama.util.Messaging.noMessages
import viper.gobra.ast.frontend.PIdnNode
import viper.gobra.frontend.info.base.SymbolTable.{GhostRegular, Predicate}
import viper.gobra.frontend.info.base.Type.Type
import viper.gobra.frontend.info.implementation.TypeInfoImpl
import viper.gobra.util.Violation.violation

trait GhostIdTyping { this: TypeInfoImpl =>

  private[typing] def wellDefGhostRegular(entity: GhostRegular, id: PIdnNode): ValidityMessages = entity match {
    case predicate: Predicate => LocalMessages(noMessages)
  }

  private[typing] def ghostEntityType(entity: GhostRegular, id: PIdnNode): Type = entity match {
    case _ => violation("untypable")
  }
}
