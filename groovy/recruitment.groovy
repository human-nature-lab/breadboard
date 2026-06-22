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

@ToString(includeNames = true, includePackage = false)
class ProlificRegisterOpts {
}

@ToString(includeNames = true, includePackage = false)
class MTurkRegisterOpts {
}

@ToString(includeNames = true, includePackage = false)
class ProlificCompleteOpts {
  String completionCode
  String message
  DateTime completedAt
  Double bonus
}

@ToString(includeNames = true, includePackage = false)
class MTurkCompleteOpts {
  Double bonus
  Boolean noFeedback
  String reason
}


recruitment = [
  registerProlific: { Vertex v, Map opts = [:] ->
    ProlificRegisterOpts registerOpts = new ProlificRegisterOpts(opts)
    _ensureSystem(v, 'recruitment')
    v._system.recruitment.source = RecruitmentSource.PROLIFIC
  },
  registerMturk: { Vertex v, Map opts = [:] ->
    MTurkRegisterOpts registerOpts = new MTurkRegisterOpts(opts)
    _ensureSystem(v, 'recruitment')
    v._system.recruitment.source = RecruitmentSource.MTURK
  },
  completeProlific: { Vertex v, Map opts = [:] ->
    ProlificCompleteOpts completeOpts = new ProlificCompleteOpts(opts)
    _ensureSystem(v, 'recruitment')
    if (v._system.recruitment.source != RecruitmentSource.PROLIFIC) {
      throw new IllegalArgumentException("Cannot complete prolific registration for non-prolific source")
    }
    if (!completeOpts.completionCode) {
      throw new IllegalArgumentException("Completion code is required")
    }
    v._system.recruitment.completed = true
    v._system.recruitment.completedAt = DateTime.now()
    v._system.recruitment.completionCode = completeOpts.completionCode
    v._system.recruitment.bonus = completeOpts.bonus
    v._system.recruitment.message = completeOpts.message
    v._system.recruitment.noFeedback = completeOpts.noFeedback
  },
  completeMturk: { Vertex v, Map opts = [:] ->
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
]
