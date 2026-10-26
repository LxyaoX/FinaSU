#ifndef __KSU_TASK_WORK_H
#define __KSU_TASK_WORK_H

#include <linux/module.h>
#include <linux/task_work.h>

/* A successful queue owns a module reference until the callback finishes. */
static inline int ksu_task_work_add(struct task_struct *task,
				    struct callback_head *work)
{
	int ret;

	if (!try_module_get(THIS_MODULE))
		return -ENODEV;
	ret = task_work_add(task, work, TWA_RESUME);
	if (ret)
		module_put(THIS_MODULE);
	return ret;
}

#endif
