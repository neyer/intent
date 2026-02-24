# Voluntas Intent Protocol

Goal: Enable Cooperative Cognition at Arbitrary Scale. 

Strategy: Accurately represent the process and structure of intentional thought.

Implementation:
 * the **process of thinking** will be represented of streams of 'ops', each one of which is both an **intent** and a **relationship to previous thoughts**. Thinking is modeled as appending additional intents to a stream of itent.
 * the **struture of thought** will be represented by a collection of Intents, which have
   * types
   * names
   * properties
  * Intents can be connected by relationships, which are, themselves, Intents. In a voluntas stream, **Everything is said, or done, for one root purpose**.  
* A given stream of intent ops deterministically produces well-defined application state. Edits, deletes, changes are all expressed as operations and their history can be presered. There is a simple, natural mapping from state->stream, in case change history is not wanted.

Multiple thinkers can then collaboratively think, together, using a shared stream. Because **all thoughts are expressed as relationships**,  thinkers can comment on, approve, dissapprove, or ask for clarifaiction on any other thoughts.

Thinkers can also propose and modify defintions of both types and functions, meaning the system becomes a **kind of programming language** as well as a **runtime**.

If lisp eliminates the distinction between 'code' and 'data', voluntas aims to eliminate the distinction between 'development' and 'runtime'. **Thinking is a mixture of development and  theexecution of code.**

The end goal of all of this is to build **a system that can allow multiple tinkers, human and machine, to cooperate with minimal friction towards a shared goal.**. 


## What the heck

I want to build a system that will allow arbitrary numbers of people to think and work, together, with as little friction as possible.

The absence of such a system causes problems in many places that most of us simply accept as "inveitable" or "part of life" rather than, "a problem which the right technology can solve." 

Protocols can, and often do, change the world. I believe **the right application layer protocol can dramatically reduce the friction in cooperative thinking**, such as evolving a software system.

Examples of the problem are:
* design documents that drift from code, and code that's written for reasons which exist only the heads of developers, rather than in an explicit, articulated place
* 'merge hell', 'release trains', developers blocked waiting on requirements from people whose job is to context switch and turn priorities and context into strings of text or snippets of audio
* worksplaces with 8,000 slack channels, all of them blinking, people pinging each other, askign for status updates, when all the data is all in the machines already, just not accessible to us
* task-tracking software that forces a specific level of granuality, missing misses the hierarchically composed nature of work, where each bash invocation or click of a button happens for a _reason_, where features are completed one keystroke at a time


These problems aren't limited to technology. Rather, tech is where they are most visible to me, and the easiest place to solve. 

Zooming out a bit, the problem absolutely shows up in poiltics. 

I have **far too many intelligent friends who disagree** on almost everything except for **the need for intelligent people to find ways of working together** that are do not devolve into a fancy version of monkeys throwing poop at one another. 

I am building a system that I think can actually solve this problem, by creating a **new protocol** and **shared computing environment** because  "how we talk" and "how we think" are, ultimatley matters of computation. 

I don't think we need to agree on what is sacred in order to better work together. I think we simply need the right lingustic and comptuational tooling. 

An analogy to neurophysiology helps here. Corproate and social media currently act as something like a shared amygdala: they drive immediate behavior through emotionanl responses to stimuli. We need that, but we also need to evolve beyond it so that we don't remain, at large scales, driven primarly by emotion.

I want to give the internet the equivalent of something like a neocortex: a tool for shared, deliberate, thought. Such a system could then be used for planning, and impulse control.

I hope this will become a 'new layer of compute', something like a new operating system, which is inherently social and trans-machine. It should allow one person to plan their own tasks and track their numerous to do items, just as well as it would allow thousands of people to cooperate on complex tasks, with work done by AI agents who can undersatnd _why_ they are doing what they are doing.

Yes, i know, it's a crazy goal. But that's what I'm aiming for.
