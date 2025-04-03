read_states = ["ReadOnly", "WriteOnly"] #, "ReadWrite"]

def incr(read_list,index=0):
    if index == len(read_list):
        return False
    read_list[index] += 1
    if read_list[index] == len(read_states):
        read_list[index] = 0
        return incr(read_list, index+1)
    return True

def mkbody(n,p=1,nindent=0):
    indent = " "*nindent
    if p > n:
        return "return chunkTask.apply("+(", ".join(["lv"+str(j) for j in range(1,n+1)]))+");"
    else:
        body = f"""
{indent}    return pi1.getUnderlying().runPartitionedReadOnly(nChunks, lv{p} -> {{
{indent}        {mkbody(n,p+1,nindent+4)}
{indent}    }});
"""
    if p == 1:
        return body.rstrip()
    else:
        return body.strip()

count = 0

for i in range(1,6):
    tlist = "<"+(", ".join(["T"+str(j) for j in range(1,i+1)]))+">"
    read_list = [0 for j in range(i)]
    while True:
        intents = ""
        partlists = ""
        for j in range(1,i+1):
            if j == i:
                end = ") {"
                endp = "> chunkTask,"
            else:
                end = ", "
                endp = ","
            rw = read_states[read_list[j-1]]
            #print("rw:",rw,read_list,j-1)
            partlists += f"""
                                                                {rw}PartListView<T{j}>{endp}"""
            intents += f"""
                                                             {rw}PartIntent<T{j}> pi{j}{end}"""
        template = f"""
public static {tlist} CompletableFuture<Void> runPartitioned(int nChunks,
                                                             PartTask{i}<{partlists}"""
        template += intents
        plist = ", ".join(["p"+str(j) for j in range(1,i+1)])
        template += f"""
    return runPartitioned(nChunks, 0, {plist});
}}"""
        print(template)
        #====================
        template = f"""
public static {tlist} CompletableFuture<Void> runPartitioned(int nChunks, int nGhosts,
                                                             PartTask{i}<{partlists}"""
        template += intents
        template += mkbody(i) + "\n}"
        print(template)
        #====================
        count += 1
        r = incr(read_list)
        #print(r, read_list)
        if not r:
            break
print("Count:", count)
