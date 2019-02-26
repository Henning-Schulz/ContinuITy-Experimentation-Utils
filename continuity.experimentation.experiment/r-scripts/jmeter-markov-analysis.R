args = commandArgs(trailingOnly=TRUE)

if (length(args)==0) {
  stop("The folder containing the experiment results needs to be supplied!", call.=FALSE)
}

# Markov calculations

fundamental.matrix <- function(full.matrix) {
  n <- nrow(full.matrix)
  q <- as.matrix(full.matrix[-n,-n])
  
  solve(diag(n-1) - q)
}

expected.requests.per.session <- function(matrix) {
  fund.matrix <- fundamental.matrix(matrix)
  
  as.matrix(t(fund.matrix)[-1,1])
}

expected.steps.per.session <- function(matrix) {
  fund.matrix <- fundamental.matrix(matrix)
  n <- nrow(fund.matrix)
  
  t <- fund.matrix %*% rep(1, n)
  t[1]
}

# TODO: Unclear how long to run it
expected.think.time.per.session <- function(transition.matrix, think.time.matrix, eps = 0.000001) {
  if (sum(think.time.matrix) == 0) {
    0
  } else {
    m <- transition.matrix
    n <- nrow(m)
    vector.one <- c(rep(1, n))
    r <- t(c(1, rep(0, n-1)))
    m.t <- transition.matrix * think.time.matrix
    
    think.time <- 0
    new.think.time <- 2 * eps
    
    while (think.time == 0 || new.think.time / think.time > eps) {
      new.think.time <- r %*% m.t %*% vector.one
      think.time <- think.time + new.think.time
      r <- r %*% m
    }
    
    think.time[1,1] # TODO: This is ms, right?
  }
}

# TODO: requests.per.session / session.duration
expected.requests.per.second <- function(requests.per.session, think.time.per.session) {
  n <- length(requests.per.session[1,])
  
}

# matrix parsing

read.full.matrix <- function(behavior.number) {
  full.matrix <- read.csv(paste("gen_behavior_model", behavior.number, ".csv", sep = ""), row.names = 1)
  full.matrix[] <- sapply(full.matrix, as.character)
  as.matrix(full.matrix)
}

max.behavior.number <- function() {
  length(list.files(pattern = "gen_behavior_model\\d")) - 1
}

read.transition.matrix <- function(behavior.number) {
  transition.matrix <- read.full.matrix(behavior.number)
  transition.matrix[] <- sapply(strsplit(transition.matrix, "; "), `[`, 1)
  
  transition.matrix <- rbind(transition.matrix, c(rep(0, ncol(transition.matrix)-1), 1))
  rownames(transition.matrix)[nrow(transition.matrix)] <- "$"
  class(transition.matrix) <- "numeric"
  
  transition.matrix
}

read.think.time.mean.matrix <- function(behavior.number) {
  think.time.matrix <- read.full.matrix(behavior.number)
  think.time.matrix[] <- sapply(strsplit(think.time.matrix, "; "), `[`, 2)
  think.time.matrix[] <- substring(sapply(strsplit(think.time.matrix, " "), `[`, 1), 6)
  
  think.time.matrix <- rbind(think.time.matrix, rep(0, ncol(think.time.matrix)))
  rownames(think.time.matrix)[nrow(think.time.matrix)] <- "$"
  class(think.time.matrix) <- "numeric"
  
  think.time.matrix
}

read.behavior.frequency <- function(behavior.number) {
  regex <- paste("gen_behavior_model", behavior.number, "</stringProp>\\n\\s+<doubleProp>\\n\\s+<name>Behavior.frequency</name>\\n\\s+<value>([0-9.]+)", sep = "")
  file.name <- "testplan.jmx"
  jmeter.content <- readChar(file.name, file.info(file.name)$size)
  
  as.numeric(stringr::str_match(jmeter.content, regex)[2])
}

read.number.of.threads <- function() {
  regex <- "<intProp name=\"ThreadGroup.num_threads\">(\\d+)</intProp>";
  file.name <- "testplan.jmx"
  jmeter.content <- readChar(file.name, file.info(file.name)$size)
  
  as.numeric(stringr::str_match(jmeter.content, regex)[2])
}

overall.expected.requests.per.session <- function() {
  max.behavior.num <- max.behavior.number()
  
  for (i in 0:max.behavior.num) {
    transition.matrix <- read.transition.matrix(i)
    behavior.freq <- read.behavior.frequency(i)
    reqs.per.session <- expected.requests.per.session(transition.matrix)
    
    if (i == 0) {
      request.matrix <- reqs.per.session
      average.request.rates <- behavior.freq * reqs.per.session
    } else {
      request.matrix <- cbind(request.matrix, reqs.per.session)
      average.request.rates <- average.request.rates + behavior.freq * reqs.per.session
    }
    
    colnames(request.matrix)[i + 1] <- paste("gen_behavior_model", i, " (", 100 * behavior.freq, "%)", sep = "")
  }
  
  request.matrix <- cbind(request.matrix, average.request.rates)
  colnames(request.matrix)[max.behavior.num + 2] <- "average"
  
  request.matrix
}

overall.expected.steps.per.session <- function() {
  max.behavior.num <- max.behavior.number()
  
  for (i in 0:max.behavior.num) {
    transition.matrix <- read.transition.matrix(i)
    behavior.freq <- read.behavior.frequency(i)
    steps.per.session <- expected.steps.per.session(transition.matrix)
    
    if (i == 0) {
      step.matrix <- as.matrix(steps.per.session, nrow = 1)
      average.steps <- behavior.freq * steps.per.session
    } else {
      step.matrix <- cbind(step.matrix, steps.per.session)
      average.steps <- average.steps + behavior.freq * steps.per.session
    }
    
    colnames(step.matrix)[i + 1] <- paste("gen_behavior_model", i, " (", 100 * behavior.freq, "%)", sep = "")
  }
  
  step.matrix <- cbind(step.matrix, average.steps)
  colnames(step.matrix)[max.behavior.num + 2] <- "average"
  
  step.matrix
}

overall.expected.think.time.per.session <- function() {
  max.behavior.num <- max.behavior.number()
  
  for (i in 0:max.behavior.num) {
    transition.matrix <- read.transition.matrix(i)
    think.time.matrix <- read.think.time.mean.matrix(i)
    behavior.freq <- read.behavior.frequency(i)
    
    if (behavior.freq > 0) {
      think.time <- expected.think.time.per.session(transition.matrix, think.time.matrix)
    } else {
      think.time <- 0
    }
    
    if (i == 0) {
      think.time.per.model.matrix <- as.matrix(think.time, nrow = 1)
      average.think.time <- behavior.freq * think.time
    } else {
      think.time.per.model.matrix <- cbind(think.time.per.model.matrix, think.time)
      average.think.time <- average.think.time + behavior.freq * think.time
    }
    
    colnames(think.time.per.model.matrix)[i + 1] <- paste("gen_behavior_model", i, " (", 100 * behavior.freq, "%)", sep = "")
  }
  
  think.time.per.model.matrix <- cbind(think.time.per.model.matrix, average.think.time)
  colnames(think.time.per.model.matrix)[max.behavior.num + 2] <- "average"
  
  think.time.per.model.matrix
}

summary <- function(req.per.session, req.per.second, think.time.per.session, number.of.threads) {
  num.rows <- nrow(req.per.session) + 1
  
  # parallel sessions
  summary.matrix <- as.matrix(rep(number.of.threads, num.rows), ncol = 1)
  
  if (num.rows == 2) {
    rownames(summary.matrix) <- c(rownames(read.transition.matrix(0))[2], "sum")
  } else {
    rownames(summary.matrix) <- c(rownames(req.per.session), "sum")
  }
  
  # duration & think time
  summary.matrix <- cbind(summary.matrix, as.matrix(rep(think.time.per.session[, "average"], num.rows), ncol = 1))
  
  # requests per session
  summary.matrix <- cbind(summary.matrix, as.matrix(c(req.per.session[, "average"], sum(req.per.session[, "average"])), ncol = 1))
  
  # requests per second
  summary.matrix <- cbind(summary.matrix, as.matrix(c(req.per.second, sum(req.per.second)), ncol = 1))
  
  # column names
  colnames(summary.matrix) <- c("parallel_sessions", "think_time_ms_per_session", "req_per_session", "req_per_second")
  
  summary.matrix
}

analyze.all <- function() {
  req.per.session <- overall.expected.requests.per.session()
  steps.per.session <- overall.expected.steps.per.session()
  think.time.per.session <- overall.expected.think.time.per.session()
  number.of.threads <- read.number.of.threads()
  
  req.per.second <- as.matrix(1000 * req.per.session[, "average"] * number.of.threads / think.time.per.session[, "average"])
  colnames(req.per.second) <- c("average")
  
  summary <- summary(req.per.session, req.per.second, think.time.per.session, number.of.threads)
  
  dir.create("analysis", showWarnings = FALSE)
  
  write.csv(req.per.session, "analysis/requests-per-session.csv", quote = FALSE)
  write.csv(req.per.second, "analysis/requests-per-second.csv", quote = FALSE)
  write.csv(steps.per.session, "analysis/steps-per-session.csv", quote = FALSE)
  write.csv(think.time.per.session, "analysis/think-time-per-session.csv", quote = FALSE)
  write.csv(summary, "analysis/summary.csv", quote = FALSE)
}

go.to.experiment <- function(results.folder, test.combination) {
  setwd(paste(results.folder, "/4-modularized-load-tests/", test.combination, "/test-creation/thread#1/jmeter-1", sep = ""))
}

####

root.dir <- args[1]
root.dir <- "/Users/hsh/git/ContinuITy-Experimentation-Utils/continuity.experimentation.experiment/exp-modularization"

setwd(root.dir)

for (test.combination in list.dirs("4-modularized-load-tests", recursive = FALSE, full.names = FALSE)) {
  go.to.experiment(root.dir, test.combination)
  
  if (file.exists("gen_behavior_model0.csv")) {
    print(paste("Analyzing", test.combination))
    analyze.all()
  }
}
