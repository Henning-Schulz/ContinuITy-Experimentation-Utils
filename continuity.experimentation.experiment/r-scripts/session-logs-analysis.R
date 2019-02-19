args = commandArgs(trailingOnly=TRUE)

if (length(args)==0) {
  stop("The folder containing the experiment results needs to be supplied!", call.=FALSE)
}


read.sessions <- function() {
  file.name <- "session-logs.json"
  session.logs <- readChar(file.name, file.info(file.name)$size)
  strsplit(session.logs, "\\n", fixed = TRUE)[[1]]
}

extract.requests <- function(session) {
  stringr::str_match_all(session, "\\\\\"(\\w+)\\\\\"")[[1]][, 2]
}

session.start.time <- function(session) {
  as.numeric(stringr::str_match_all(session, ":(\\d{19})")[[1]][1, 2])
}

session.end.time <- function(session) {
  timestamps <- as.numeric(stringr::str_match_all(session, ":(\\d{19})")[[1]][, 2])
  timestamps[length(timestamps)]
}

avg.sessions.per.millisecond <- function(split.logs) {
  start.time <- min(sapply(split.logs, session.start.time))
  end.time <- max(sapply(split.logs, session.end.time))
  
  length(split.logs) / (end.time - start.time) * 1000000
}

avg.requests.per.session <- function(split.logs) {
  sum.requests <- NULL
  
  for (session in split.logs) {
    requests <- extract.requests(session)
    requests.per.endpoint <- as.matrix(table(requests))
    
    if (is.null(sum.requests)) {
      sum.requests <- requests.per.endpoint
    } else {
      requests.per.endpoint <- requests.per.endpoint[match(rownames(sum.requests), rownames(requests.per.endpoint))]
      requests.per.endpoint[is.na(requests.per.endpoint)] <- 0
      sum.requests <- sum.requests + requests.per.endpoint
    }
  }
  
  colnames(sum.requests)[1] <- "session_logs"
  sum.requests / length(split.logs)
}

avg.steps.per.session <- function(split.logs) {
  sum.steps <- 0
  
  for (session in split.logs) {
    requests <- extract.requests(session)
    sum.steps <- sum.steps + length(requests)
  }
  
  sum.steps / length(split.logs)
}

avg.session.duration <- function(split.logs) {
  sum.length <- 0
  
  for (session in split.logs) {
    timestamps <- as.numeric(stringr::str_match_all(session, ":(\\d{19})")[[1]][, 2])
    sum.length <- sum.length + (timestamps[length(timestamps)] - timestamps[1])
  }
  
  sum.length / length(split.logs) / 1000000 # ms
}

avg.summarized.think.time <- function(split.logs) {
  sum.think.times <- 0
  
  for (session in split.logs) {
    timestamps <- as.numeric(stringr::str_match_all(session, ":(\\d{19})")[[1]][, 2])
    timestamps <- timestamps[c(-1, -length(timestamps))]
    factors = rep(c(-1, 1), length(timestamps) / 2)
    
    sum.think.times <- sum.think.times + sum(factors * timestamps)
  }
  
  sum.think.times / length(split.logs) / 1000000 # ms
}

avg.session.duration.and.think.time <- function(split.logs) {
  result <- cbind(avg.session.duration(split.logs), avg.summarized.think.time(split.logs))
  colnames(result) <- c("duration", "think_time")
  result
}

summary <- function(req.per.session, req.per.second, duration.and.think.time.per.session, sessions.per.millisecond) {
  parallel.sessions <- (sessions.per.millisecond * duration.and.think.time.per.session[, "think_time"])
  num.rows <- nrow(req.per.session) + 1
  
  # parallel sessions
  summary.matrix <- as.matrix(rep(parallel.sessions, num.rows), ncol = 1)
  rownames(summary.matrix) <- c(rownames(req.per.session), "sum")
  
  # duration & think time
  summary.matrix <- cbind(summary.matrix, as.matrix(rep(duration.and.think.time.per.session[, "duration"], num.rows), ncol = 1))
  summary.matrix <- cbind(summary.matrix, as.matrix(rep(duration.and.think.time.per.session[, "think_time"], num.rows), ncol = 1))
  
  # requests per session
  summary.matrix <- cbind(summary.matrix, as.matrix(c(req.per.session, sum(req.per.session)), ncol = 1))
  
  # requests per second
  summary.matrix <- cbind(summary.matrix, as.matrix(c(req.per.second, sum(req.per.second)), ncol = 1))
  
  # column names
  colnames(summary.matrix) <- c("parallel_sessions", "session_duration_ms", "think_time_ms_per_session", "req_per_session", "req_per_second")
  
  summary.matrix
}

analyze.all <- function() {
  split.sessions <- read.sessions()
  req.per.session <- avg.requests.per.session(split.sessions)
  steps.per.session <- avg.steps.per.session(split.sessions)
  duration.and.think.time.per.session <- avg.session.duration.and.think.time(split.sessions)
  sessions.per.millisecond <- avg.sessions.per.millisecond(split.sessions)
  
  req.per.second <- as.matrix(1000 * sessions.per.millisecond * req.per.session[, "session_logs"])
  colnames(req.per.second) <- c("session_logs")
  
  summary <- summary(req.per.session, req.per.second, duration.and.think.time.per.session, sessions.per.millisecond)
  
  dir.create("session-analysis", showWarnings = FALSE)
  
  write.csv(req.per.session, "session-analysis/requests-per-session.csv", quote = FALSE)
  write.csv(req.per.second, "session-analysis/requests-per-second.csv", quote = FALSE)
  write.csv(steps.per.session, "session-analysis/steps-per-session.csv", quote = FALSE)
  write.csv(duration.and.think.time.per.session, "session-analysis/session-duration.csv", quote = FALSE)
  write.csv(summary, "session-analysis/summary.csv", quote = FALSE)
}

go.to.experiment <- function(results.folder, test.combination) {
  setwd(paste(results.folder, "/4-modularized-load-tests/", test.combination, "/test-creation/thread#1", sep = ""))
}

go.to.reference <- function(results.folder) {
  setwd(paste(results.folder, "/3-session-logs-creation/", sep = ""))
}

root.dir <- args[1]
root.dir <- "/Users/hsh/git/ContinuITy-Experimentation-Utils/continuity.experimentation.experiment/exp-modularization"

setwd(root.dir)

for (test.combination in list.dirs("4-modularized-load-tests", recursive = FALSE, full.names = FALSE)) {
  go.to.experiment(root.dir, test.combination)
  
  if (file.exists("session-logs.json")) {
    print(paste("Analyzing", test.combination))
    analyze.all()
  }
}

go.to.reference(root.dir)
analyze.all()
