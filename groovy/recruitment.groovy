import com.tinkerpop.blueprints.Vertex
import groovy.transform.ToString
import org.joda.time.DateTime

// Recruitment source identifiers. Kept as a plain map of strings (not an enum) so the
// value written to player._system.recruitment.source is exactly the string sent to the
// client -- the frontend compares it directly (see BBMain.vue isProlific / isMTurk).
RecruitmentSource = [
  PROLIFIC: 'prolific',
  MTURK: 'mturk',
].asImmutable()

@ToString(includeNames = true)
class ProlificRegisterOpts {
}

@ToString(includeNames = true)
class MTurkRegisterOpts {
  Boolean sandbox
}

@ToString(includeNames = true)
class ProlificCompleteOpts {
  String completionCode
  String message
  DateTime completedAt
  Double bonus
  Boolean noFeedback
}

@ToString(includeNames = true)
class MTurkCompleteOpts {
  Double bonus
  Boolean noFeedback
  String reason
}

private class RecruitmentController extends BreadboardBase {

  Boolean recruitmentActive = true
  TinkerGraph graph
  Set completedPlayerIds = [] as Set
  Set inGamePlayerIds = [] as Set
  Set pendingPlayerIds = [] as Set

  RecruitmentController(TinkerGraph graph) {
    this.graph = graph
  }

  public setInGamePlayer(...playerIds){
    inGamePlayerIds.addAll(playerIds)
    pendingPlayerIds.removeAll(playerIds)
  }

  public stopRecruitingProlific(Map opts = [:]) {
    recruitmentActive = false
    for (String playerId in pendingPlayerIds) {
      pendingPlayerIds.remove(playerId)
      def v = graph.getVertex(playerId)
      completeProlific(v, opts)
    }
  }

  public stopRecruitingMturk(Map opts = [:]) {
    recruitmentActive = false
    for (String playerId in pendingPlayerIds) {
      pendingPlayerIds.remove(playerId)
      def v = graph.getVertex(playerId)
      completeMturk(v, opts)
    }
  }

  public registerProlific(Vertex v, Map opts = [:]) {
    if (!recruitmentActive) return
    ProlificRegisterOpts registerOpts = new ProlificRegisterOpts(opts)
    _ensureSystem(v, 'recruitment')
    v._system.recruitment.source = RecruitmentSource.PROLIFIC
    pendingPlayerIds.add(v.id)
  }

  public completeProlific(Vertex v, Map opts = [:]) {
    pendingPlayerIds.remove(v.id)
    completedPlayerIds.add(v.id)
    ProlificCompleteOpts completeOpts = new ProlificCompleteOpts(opts)
    _ensureSystem(v, 'recruitment')
    if (v._system.recruitment.source != RecruitmentSource.PROLIFIC) {
      throw new IllegalArgumentException("Cannot complete prolific registration for non-prolific source")
    }
    v._system.recruitment.completed = true
    v._system.recruitment.completedAt = DateTime.now()
    v._system.recruitment.completionCode = completeOpts.completionCode
    v._system.recruitment.bonus = completeOpts.bonus
    v._system.recruitment.message = completeOpts.message
    v._system.recruitment.noFeedback = completeOpts.noFeedback
  }

  public registerMturk(Vertex v, Map opts = [:]) {
    if (!recruitmentActive) return
    pendingPlayerIds.add(v.id)
    MTurkRegisterOpts registerOpts = new MTurkRegisterOpts(opts)
    _ensureSystem(v, 'recruitment')
    v._system.recruitment.source = RecruitmentSource.MTURK
    v._system.recruitment.sandbox = registerOpts.sandbox
  }

  public completeMturk(Vertex v, Map opts = [:]) {
    pendingPlayerIds.remove(v.id)
    completedPlayerIds.add(v.id)
    MTurkCompleteOpts completeOpts = new MTurkCompleteOpts(opts)
    _ensureSystem(v, 'recruitment')
    if (v._system.recruitment.source != RecruitmentSource.MTURK) {
      throw new IllegalArgumentException("Cannot complete mturk registration for non-mturk source")
    }
    if (!completeOpts.bonus) {
      throw new IllegalArgumentException("Bonus is required")
    }
    v._system.recruitment.completed = true
    v._system.recruitment.noFeedback = completeOpts.noFeedback
    v._system.recruitment.reason = completeOpts.reason
    v._system.recruitment.bonus = completeOpts.bonus
  }
}

recruitment = new RecruitmentController()