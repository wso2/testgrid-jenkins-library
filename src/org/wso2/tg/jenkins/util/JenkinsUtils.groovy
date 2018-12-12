import hudson.model.Hudson

/**
 * Get Jenkins job object
 * @param jobName job name
 * @return job object that matches jobName
 */
def getJobByName(jobName){
    for(item in Hudson.instance.items) {
        if(item.name == jobName){
            return item
        }
    }
}