Removing unreferenced code from Project Oberon 2013
===================================================

This patch removes unreferenced code (variables/procedures) from Project Oberon 2013.

Command procedures (with the exception of `System.ExtendDisplay` which does not work)
and object allocators have not been removed, despite being (statically) unreferenced.

The resulting system is working just like the original.
