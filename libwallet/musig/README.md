CGo likes having all C files in the same folder as the go package that will use it. This can be avoided, but it requires building the lib ourselves. In the context of libwallet, that means cross compiling to iOS and Android targets, then selectively linking the proper one. Not exactly easy.

The alternative is then to flatten libsecp256k1 to a single folder. We can now use golangs include directives to use the headers we need. So far so good, right?

Wrong. The lib has a peculiar pattern of writing a lot of it's logic in .h files instead of .c files. CGo naturally only compiles .c files. To get around this, a new .c file is added: umbrella.c, which includes every source header we need. It's counterpart, umbrella.h includes every definition header we need to make things a bit easier to handle on Go's side.

Some things to keep in mind if you want to update libsecp. The script does it best job to make everything work, but it might fail if any details change in the lib. After executing it, review added files to see if they are relevant and remove them if not. 
