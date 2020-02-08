package com.couchmate.data.db.query

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.ProviderOwnerTable

trait ProviderOwnerQueries {

  private[db] lazy val getProviderOwner = Compiled { (providerOwnerId: Rep[Long]) =>
    ProviderOwnerTable.table.filter(_.providerOwnerId === providerOwnerId)
  }

  private[db] lazy val getProviderOwnerForName = Compiled { (name: Rep[String]) =>
    ProviderOwnerTable.table.filter(_.name === name)
  }

  private[db] lazy val getProviderOwnerForExt = Compiled { (extProviderOwnerId: Rep[String]) =>
    ProviderOwnerTable.table.filter(_.extProviderOwnerId === extProviderOwnerId)
  }

}
