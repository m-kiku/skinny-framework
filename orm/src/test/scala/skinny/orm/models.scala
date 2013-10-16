package skinny.orm

import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import scalikejdbc._, SQLInterpolation._
import skinny.orm._
import skinny.orm.feature._

case class Member(
  id: Long,
  name: Option[Name] = None,
  countryId: Long,
  mentorId: Option[Long],
  companyId: Option[Long],
  createdAt: DateTime,
  country: Country,
  company: Option[Company] = None,
  mentor: Option[Member] = None,
  mentorees: Seq[Member] = Nil,
  groups: Seq[Group] = Nil,
  skills: Seq[Skill] = Nil)

object Member extends SkinnyCRUDMapper[Member] {
  override val tableName = "members"
  override val defaultAlias = createAlias("m")
  val mentorAlias = createAlias("mentor")
  val mentoreeAlias = createAlias("mentoree")

  // if you use hasOne, joined entity should be Option[Entity]
  innerJoinWithDefaults(Country, (m, c) => sqls.eq(m.countryId, c.id)).byDefaultEvenIfAssociated

  // one-to-one
  belongsTo[Company](Company, (m, c) => m.copy(company = c)).byDefault
  belongsToWithAlias[Member](Member -> Member.mentorAlias, (m, mentor) => m.copy(mentor = mentor)).byDefault
  hasOne[Name](Name, (m, name) => m.copy(name = name)).byDefault

  // groups
  hasManyThroughWithFk[Group](
    GroupMember, GroupMapper, "memberId", "groupId", (member, gs) => member.copy(groups = gs)
  ).byDefault
  // if GroupMapper is "Group", this code will work
  //hasManyThrough[Group](GroupMember, Group, (member, groups) => member.copy(groups = groups)).byDefault

  // skills
  val skills = hasManyThrough[Skill](
    MemberSkill, Skill, (member, ss) => member.copy(skills = ss))

  // mentorees
  hasMany[Member](
    many = Member -> Member.mentoreeAlias,
    on = (m, mentorees) => sqls.eq(m.id, mentorees.mentorId),
    merge = (member, mentorees) => member.copy(mentorees = mentorees)
  ).byDefault

  override def extract(rs: WrappedResultSet, n: ResultName[Member]): Member = new Member(
    id = rs.long(n.id),
    countryId = rs.long(n.countryId),
    companyId = rs.longOpt(n.companyId),
    mentorId = rs.longOpt(n.mentorId),
    createdAt = rs.dateTime(n.createdAt),
    country = Country(rs)
  )
}

case class Name(memberId: Long, first: String, last: String, createdAt: DateTime, updatedAt: Option[DateTime] = None, member: Option[Member] = None)

object Name extends SkinnyCRUDMapper[Name]
    with TimestampsFeature[Name]
    with OptimisticLockWithTimestampFeature[Name] {

  override val tableName = "names"
  override val lockTimestampFieldName = "updatedAt"

  override val useAutoIncrementPrimaryKey = false
  override val primaryKeyName = "memberId"

  override val defaultAlias = createAlias("nm")

  val member = belongsTo[Member](Member, (name, member) => name.copy(member = member)).byDefault

  def extract(rs: WrappedResultSet, s: ResultName[Name]): Name = new Name(
    memberId = rs.long(s.memberId),
    first = rs.string(s.first),
    last = rs.string(s.last),
    createdAt = rs.dateTime(s.createdAt),
    updatedAt = rs.dateTimeOpt(s.updatedAt)
  )
}

case class Company(id: Option[Long] = None, name: String) extends MutableSkinnyRecord[Company] {
  def skinnyCRUDMapper = Company
}

object Company extends SkinnyCRUDMapper[Company] with SoftDeleteWithBooleanFeature[Company] {
  override val tableName = "companies"
  override val defaultAlias = createAlias("cmp")
  def extract(rs: WrappedResultSet, s: ResultName[Company]): Company = new Company(
    id = rs.longOpt(s.id),
    name = rs.string(s.name)
  )
}

case class Country(id: Long, name: String) extends SkinnyRecord[Country] {
  def skinnyCRUDMapper = Country
}

object Country extends SkinnyCRUDMapper[Country] {
  override val tableName = "countries"
  override val defaultAlias = createAlias("cnt")
  def extract(rs: WrappedResultSet, s: ResultName[Country]): Country = new Country(
    id = rs.long(s.id), name = rs.string(s.name)
  )
}

case class Group(id: Long, name: String)

// using different name is ok though a little bit verbose, mapper must not be the companion.
object GroupMapper extends SkinnyCRUDMapper[Group] with SoftDeleteWithTimestampFeature[Group] {
  override val tableName = "groups"
  override val defaultAlias = createAlias("g")
  def extract(rs: WrappedResultSet, s: ResultName[Group]): Group = new Group(
    id = rs.long(s.id),
    name = rs.string(s.name)
  )

  private[this] val logger = LoggerFactory.getLogger(classOf[Group])
  override protected def beforeCreate(namedValues: Seq[(SQLSyntax, Any)])(implicit s: DBSession) = {
    super.beforeCreate(namedValues)(s)
    logger.info(s"Before creation. params: ${namedValues}")
  }
  override protected def afterCreate(namedValues: Seq[(SQLSyntax, Any)], generatedId: Option[Long])(implicit s: DBSession) = {
    super.afterCreate(namedValues, generatedId)(s)
    logger.info(s"Created Group's id: ${generatedId}")
  }
}

case class GroupMember(groupId: Long, memberId: Long)

object GroupMember extends SkinnyJoinTable[GroupMember] {
  override val tableName = "groups_members"
  override val defaultAlias = createAlias("gm")
}

case class Skill(id: Long, name: String, createdAt: DateTime, updatedAt: Option[DateTime] = None, lockVersion: Long)

object Skill extends SkinnyCRUDMapper[Skill] with TimestampsFeature[Skill] with OptimisticLockWithVersionFeature[Skill] {
  override val tableName = "skills"
  override val defaultAlias = createAlias("s")
  def extract(rs: WrappedResultSet, s: ResultName[Skill]): Skill = new Skill(
    id = rs.long(s.id),
    name = rs.string(s.name),
    createdAt = rs.dateTime(s.createdAt),
    updatedAt = rs.dateTimeOpt(s.updatedAt),
    lockVersion = rs.long(s.lockVersion)
  )
}

case class MemberSkill(memberId: Long, skillId: Long)

object MemberSkill extends SkinnyJoinTable[MemberSkill] {
  override val tableName = "members_skills"
  override val defaultAlias = createAlias("ms")
}